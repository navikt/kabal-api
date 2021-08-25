package no.nav.klage.oppgave.clients.joark

import brave.Tracer
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.ArkivertDokumentWithTitle
import no.nav.klage.oppgave.domain.joark.*
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.kodeverk.PartIdType
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.apache.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

@Component
class JoarkClient(
    private val joarkWebClient: WebClient,
    private val tokenUtil: TokenUtil,
    private val tracer: Tracer,
    private val pdlFacade: PdlFacade,
    private val eregClient: EregClient
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        private const val BREV_TITTEL = "Brev fra Klageinstans"
        private const val BREVKODE = "BREV_FRA_KLAGEINSTANS"
        private const val BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS = "ab0164"
    }

    fun createJournalpost(
        klagebehandling: Klagebehandling,
        journalfoerendeEnhet: String,
        document: ArkivertDokumentWithTitle
    ): String {

        val journalpost = this.createJournalpostObject(klagebehandling, journalfoerendeEnhet, document)

        val journalpostResponse = joarkWebClient.post()

            .header("Nav-Consumer-Token", "Bearer ${tokenUtil.getStsSystembrukerToken()}")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithJoarkScope()}")
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(journalpost)
            .retrieve()
            .bodyToMono(JournalpostResponse::class.java)
            .block()
            ?: throw RuntimeException("Journalpost could not be created for klagebehandling with id ${klagebehandling.id}.")

        logger.debug("Journalpost successfully created in Joark with id {}.", journalpostResponse.journalpostId)

        return journalpostResponse.journalpostId
    }

    fun cancelJournalpost(journalpostId: String): String {
        val response = joarkWebClient.patch()
            .uri("/${journalpostId}/feilregistrer/settStatusAvbryt")
            .header("Nav-Consumer-Token", "Bearer ${tokenUtil.getStsSystembrukerToken()}")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithJoarkScope()}")
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(AvbrytJournalpostPayload(journalpostId))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
            ?: throw RuntimeException("Journalpost with id $journalpostId could not be cancelled.")

        logger.debug("Journalpost with id $journalpostId was succesfully cancelled.")

        return response
    }


    fun finalizeJournalpost(journalpostId: String, journalfoerendeEnhet: String): String {
        val response = joarkWebClient.patch()
            .uri("/${journalpostId}/ferdigstill")
            .header("Nav-Consumer-Token", "Bearer ${tokenUtil.getStsSystembrukerToken()}")
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithJoarkScope()}")
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(FerdigstillJournalpostPayload(journalfoerendeEnhet))
            .retrieve()
            .bodyToMono(String::class.java)
            .block()
            ?: throw RuntimeException("Journalpost with id $journalpostId could not be finalized.")

        logger.debug("Journalpost with id $journalpostId was succesfully finalized.")

        return response
    }

    private fun createJournalpostObject(
        klagebehandling: Klagebehandling,
        journalfoerendeEnhet: String,
        document: ArkivertDokumentWithTitle
    ): Journalpost =
        Journalpost(
            journalposttype = JournalpostType.UTGAAENDE,
            tema = klagebehandling.tema,
            behandlingstema = BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS,
            avsenderMottaker = createAvsenderMottager(klagebehandling),
            sak = createSak(klagebehandling),
            tittel = BREV_TITTEL,
            journalfoerendeEnhet = journalfoerendeEnhet,
            eksternReferanseId = tracer.currentSpan().context().traceIdString(),
            bruker = createBruker(klagebehandling),
            dokumenter = createDokument(document)
        )


    private fun createAvsenderMottager(klagebehandling: Klagebehandling): AvsenderMottaker? {
        val klager = klagebehandling.klager
        if (klager.partId.type.equals(PartIdType.PERSON)) {
            val person = pdlFacade.getPersonInfo(klager.partId.value)
            return person.let {
                AvsenderMottaker(
                    id = it.foedselsnr,
                    idType = AvsenderMottakerIdType.FNR,
                    navn = it.settSammenNavn()
                )
            }
        } else {
            return AvsenderMottaker(
                klager.partId.value,
                AvsenderMottakerIdType.ORGNR,
                //TODO: Finn en bedre løsning på dette etter hvert
                navn = eregClient.hentOrganisasjon(klager.partId.value)?.navn?.navnelinje1
            )
        }
    }

    private fun createSak(klagebehandling: Klagebehandling): Sak {
        return if (klagebehandling.sakFagsakId == null || klagebehandling.sakFagsystem == null) {
            Sak(Sakstype.GENERELL_SAK)
        } else {
            Sak(
                sakstype = Sakstype.FAGSAK,
                fagsaksystem = klagebehandling.sakFagsystem?.navn?.let { FagsaksSystem.valueOf(it) },
                fagsakid = klagebehandling.sakFagsakId
            )
        }
    }

    private fun createBruker(klagebehandling: Klagebehandling): Bruker? {
        return klagebehandling.sakenGjelder.partId.let {
            Bruker(
                it.value,
                BrukerIdType.FNR
            )
        }
    }


    private fun createDokument(
        document: ArkivertDokumentWithTitle
    ): List<Dokument> {
        val hovedDokument = Dokument(
            tittel = BREV_TITTEL,
            brevkode = BREVKODE,
            dokumentVarianter = listOf(
                DokumentVariant(
                    filnavn = document.title,
                    filtype = "PDF",
                    variantformat = "ARKIV",
                    fysiskDokument = Base64.getEncoder().encodeToString(document.content)
                )
            ),

            )
        return listOf(hovedDokument)
    }
}