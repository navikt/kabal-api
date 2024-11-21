package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import jakarta.transaction.Transactional
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.ytelseTilRegistreringshjemlerV2
import no.nav.klage.oppgave.api.view.OversendtAnkeITrygderettenV1
import no.nav.klage.oppgave.api.view.createAnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag
import no.nav.klage.oppgave.domain.klage.Felt
import no.nav.klage.oppgave.repositories.AnkeITrygderettenbehandlingRepository
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.interceptor.TransactionAspectSupport
import java.util.*

@Service
@Transactional
class AnkeITrygderettenbehandlingService(
    private val ankeITrygderettenbehandlingRepository: AnkeITrygderettenbehandlingRepository,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val kafkaEventRepository: KafkaEventRepository,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val mottakService: MottakService,
    private val dokumentService: DokumentService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val objectMapperBehandlingEvents = ObjectMapper().registerModule(JavaTimeModule()).configure(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false
        )
    }

    fun createAnkeITrygderettenbehandling(input: AnkeITrygderettenbehandlingInput): AnkeITrygderettenbehandling {
        logger.debug("createAnkeITrygderettenbehandling: transactionId: " + TransactionAspectSupport.currentTransactionStatus().hashCode())
        val ankeITrygderettenbehandling = ankeITrygderettenbehandlingRepository.save(
            AnkeITrygderettenbehandling(
                klager = input.klager.copy(),
                sakenGjelder = input.sakenGjelder?.copy() ?: input.klager.toSakenGjelder(),
                ytelse = input.ytelse,
                type = input.type,
                kildeReferanse = input.kildeReferanse,
                dvhReferanse = input.dvhReferanse,
                fagsystem = input.fagsystem,
                fagsakId = input.fagsakId,
                mottattKlageinstans = input.sakMottattKlageinstans,
                tildeling = null,
                hjemler = if (input.innsendingsHjemler.isNullOrEmpty()) {
                    mutableSetOf(Hjemmel.MANGLER)
                } else {
                    input.innsendingsHjemler
                },
                sendtTilTrygderetten = input.sendtTilTrygderetten,
                kjennelseMottatt = null,
                previousSaksbehandlerident = input.previousSaksbehandlerident,
                gosysOppgaveId = input.gosysOppgaveId,
                tilbakekreving = input.tilbakekreving,
            )
        )
        logger.debug("Created ankeITrygderettenbehandling {}", ankeITrygderettenbehandling.id)

        if (input.registreringsHjemmelSet != null) {

            //TODO: Oppdater om det kommer ny versjon
            val washedRegistreringshjemmelSet = input.registreringsHjemmelSet.filter {
                ytelseTilRegistreringshjemlerV2[input.ytelse]?.contains(it) ?: false
            }.toSet()

            behandlingService.setRegistreringshjemler(
                behandlingId = ankeITrygderettenbehandling.id,
                registreringshjemler = washedRegistreringshjemmelSet,
                utfoerendeSaksbehandlerIdent = systembrukerIdent,
                systemUserContext = true,
            )
        }

        behandlingService.connectDocumentsToBehandling(
            behandlingId = ankeITrygderettenbehandling.id,
            journalfoertDokumentReferenceSet = input.saksdokumenter.map {
                JournalfoertDokumentReference(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId
                )
            }.toSet(),
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingEndretEvent(
                behandling = ankeITrygderettenbehandling,
                endringslogginnslag = listOfNotNull(
                    Endringslogginnslag.endringslogg(
                        saksbehandlerident = systembrukerIdent,
                        felt = Felt.ANKE_I_TRYGDERETTEN_OPPRETTET,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = ankeITrygderettenbehandling.id,
                        tidspunkt = ankeITrygderettenbehandling.created,
                    )
                )
            )
        )

        //Publiser Kafka-event, infomelding om opprettelse
        val behandlingEvent = BehandlingEvent(
            eventId = UUID.randomUUID(),
            kildeReferanse = ankeITrygderettenbehandling.kildeReferanse,
            kilde = ankeITrygderettenbehandling.fagsystem.navn,
            kabalReferanse = ankeITrygderettenbehandling.id.toString(),
            type = BehandlingEventType.ANKE_I_TRYGDERETTENBEHANDLING_OPPRETTET,
            detaljer = BehandlingDetaljer(
                ankeITrygderettenbehandlingOpprettet =
                AnkeITrygderettenbehandlingOpprettetDetaljer(
                    sendtTilTrygderetten = ankeITrygderettenbehandling.sendtTilTrygderetten,
                    utfall = input.ankebehandlingUtfall,
                )
            )
        )
        kafkaEventRepository.save(
            KafkaEvent(
                id = UUID.randomUUID(),
                behandlingId = ankeITrygderettenbehandling.id,
                kilde = ankeITrygderettenbehandling.fagsystem.navn,
                kildeReferanse = ankeITrygderettenbehandling.kildeReferanse,
                jsonPayload = objectMapperBehandlingEvents.writeValueAsString(behandlingEvent),
                type = EventType.BEHANDLING_EVENT
            )
        )

        return ankeITrygderettenbehandling
    }

    fun createAnkeITrygderettenbehandling(input: OversendtAnkeITrygderettenV1) {
        mottakService.validateAnkeITrygderettenV1(input)
        val inputDocuments =
            dokumentService.createSaksdokumenterFromJournalpostIdList(input.tilknyttedeJournalposter.map { it.journalpostId })
        createAnkeITrygderettenbehandling(
            input.createAnkeITrygderettenbehandlingInput(inputDocuments)
        )
    }
}