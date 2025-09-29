package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.ytelseToRegistreringshjemlerV2
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandling
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.repositories.GjenopptakITrygderettenbehandlingRepository
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class GjenopptakITrygderettenbehandlingService(
    private val gjenopptakITrygderettenbehandlingRepository: GjenopptakITrygderettenbehandlingRepository,
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
        private val objectMapper = ourJacksonObjectMapper()
        private val objectMapperBehandlingEvents = ObjectMapper().registerModule(JavaTimeModule()).configure(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false
        )
    }

    fun createGjenopptakITrygderettenbehandling(input: GjenopptakITrygderettenbehandlingInput): GjenopptakITrygderettenbehandling {
        val gjenopptakITrygderettenbehandling = gjenopptakITrygderettenbehandlingRepository.save(
            GjenopptakITrygderettenbehandling(
                klager = input.klager.copy(),
                sakenGjelder = input.sakenGjelder?.copy() ?: input.klager.toSakenGjelder(),
                prosessfullmektig = input.prosessfullmektig,
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
                gosysOppgaveRequired = input.gosysOppgaveRequired,
            )
        )
        logger.debug("Created gjenopptakITrygderettenbehandling {}", gjenopptakITrygderettenbehandling.id)

        if (input.registreringsHjemmelSet != null) {
            val washedRegistreringshjemmelSet = input.registreringsHjemmelSet.filter {
                ytelseToRegistreringshjemlerV2[input.ytelse]?.contains(it) ?: false
            }.toSet()

            behandlingService.setRegistreringshjemler(
                behandlingId = gjenopptakITrygderettenbehandling.id,
                registreringshjemler = washedRegistreringshjemmelSet,
                utfoerendeSaksbehandlerIdent = systembrukerIdent,
                systemUserContext = true,
            )
        }

        behandlingService.connectDocumentsToBehandling(
            behandlingId = gjenopptakITrygderettenbehandling.id,
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
            BehandlingChangedEvent(
                behandling = gjenopptakITrygderettenbehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = systembrukerIdent,
                        felt = BehandlingChangedEvent.Felt.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_OPPRETTET,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = gjenopptakITrygderettenbehandling.id,
                    )
                )
            )
        )

        //Publiser Kafka-event, infomelding om opprettelse
        val behandlingEvent = BehandlingEvent(
            eventId = UUID.randomUUID(),
            kildeReferanse = gjenopptakITrygderettenbehandling.kildeReferanse,
            kilde = gjenopptakITrygderettenbehandling.fagsystem.navn,
            kabalReferanse = gjenopptakITrygderettenbehandling.id.toString(),
            type = BehandlingEventType.ANKE_I_TRYGDERETTENBEHANDLING_OPPRETTET,
            detaljer = BehandlingDetaljer(
                gjenopptakITrygderettenbehandlingOpprettet =
                GjenopptakITrygderettenbehandlingOpprettetDetaljer(
                    sendtTilTrygderetten = gjenopptakITrygderettenbehandling.sendtTilTrygderetten,
                    utfall = input.gjenopptakbehandlingUtfall,
                )
            )
        )
        kafkaEventRepository.save(
            KafkaEvent(
                id = UUID.randomUUID(),
                behandlingId = gjenopptakITrygderettenbehandling.id,
                kilde = gjenopptakITrygderettenbehandling.fagsystem.navn,
                kildeReferanse = gjenopptakITrygderettenbehandling.kildeReferanse,
                jsonPayload = objectMapperBehandlingEvents.writeValueAsString(behandlingEvent),
                type = EventType.BEHANDLING_EVENT
            )
        )

        return gjenopptakITrygderettenbehandling
    }
}