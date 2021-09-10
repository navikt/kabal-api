package no.nav.klage.oppgave.gateway

import brave.Tracer
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.joark.JoarkClient
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.ArkivertDokumentWithTitle
import no.nav.klage.oppgave.domain.joark.*
import no.nav.klage.oppgave.domain.klage.BrevMottaker
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.kodeverk.PartIdType
import no.nav.klage.oppgave.util.PdfUtils
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Component
import java.util.*

@Component
class JournalpostGateway(
    private val joarkClient: JoarkClient,
    private val tracer: Tracer,
    private val pdlFacade: PdlFacade,
    private val eregClient: EregClient,
    private val pdfUtils: PdfUtils
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securelogger = getSecureLogger()
        private const val BREV_TITTEL = "Brev fra Klageinstans"
        private const val BREVKODE = "BREV_FRA_KLAGEINSTANS"
        private const val BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS = "ab0164"
        private const val KLAGEBEHANDLING_ID_KEY = "klagebehandling_id"
    }

    fun createJournalpost(
        klagebehandling: Klagebehandling,
        document: ArkivertDokumentWithTitle,
        brevMottaker: BrevMottaker
    ): String {
        val journalpost = createJournalpostObject(
            klagebehandling,
            document,
            brevMottaker
        )
        return joarkClient.createJournalpostInJoark(journalpost).journalpostId
    }

    fun cancelJournalpost(journalpostId: String): String {
        return joarkClient.cancelJournalpost(journalpostId)
    }

    fun finalizeJournalpost(journalpostId: String, journalfoerendeEnhet: String): String {
        return joarkClient.finalizeJournalpost(journalpostId, journalfoerendeEnhet)
    }

    private fun createJournalpostObject(
        klagebehandling: Klagebehandling,
        document: ArkivertDokumentWithTitle,
        brevMottaker: BrevMottaker
    ): Journalpost =
        Journalpost(
            journalposttype = JournalpostType.UTGAAENDE,
            tema = klagebehandling.tema,
            behandlingstema = BEHANDLINGSTEMA_KLAGE_KLAGEINSTANS,
            avsenderMottaker = createAvsenderMottager(brevMottaker),
            sak = createSak(klagebehandling),
            tittel = BREV_TITTEL,
            journalfoerendeEnhet = klagebehandling.tildeling!!.enhet!!,
            eksternReferanseId = tracer.currentSpan().context().traceIdString(),
            bruker = createBruker(klagebehandling),
            dokumenter = createDokument(document),
            tilleggsopplysninger = listOf(Tilleggsopplysning(
                nokkel = KLAGEBEHANDLING_ID_KEY, verdi = klagebehandling.id.toString())
            )
        )


    private fun createAvsenderMottager(brevMottaker: BrevMottaker): AvsenderMottaker? {
        if (brevMottaker.partId.type == PartIdType.PERSON) {
            val person = pdlFacade.getPersonInfo(brevMottaker.partId.value)
            return person.let {
                AvsenderMottaker(
                    id = it.foedselsnr,
                    idType = AvsenderMottakerIdType.FNR,
                    navn = it.settSammenNavn()
                )
            }
        } else {
            return AvsenderMottaker(
                brevMottaker.partId.value,
                AvsenderMottakerIdType.ORGNR,
                //TODO: Finn en bedre løsning på dette etter hvert
                navn = eregClient.hentOrganisasjon(brevMottaker.partId.value)?.navn?.navnelinje1
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
                    filtype = if (pdfUtils.pdfByteArrayIsPdfa(document.content)) "PDFA" else "PDF",
                    variantformat = "ARKIV",
                    fysiskDokument = Base64.getEncoder().encodeToString(document.content)
                )
            ),

            )
        return listOf(hovedDokument)
    }
}