package no.nav.klage.oppgave.eventlisteners

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.FeilregistrertInKabalInput
import no.nav.klage.oppgave.domain.behandling.*
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.repositories.*
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.LocalDateTime
import java.util.*

@Service
class CleanupAfterBehandlingEventListener(
    private val meldingRepository: MeldingRepository,
    private val kafkaEventRepository: KafkaEventRepository,
    private val kakaApiGateway: KakaApiGateway,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val klagebehandlingRepository: KlagebehandlingRepository,
    private val ankebehandlingRepository: AnkebehandlingRepository,
    private val fssProxyClient: KlageFssProxyClient,
    private val behandlingService: BehandlingService,
    private val mergedDocumentRepository: MergedDocumentRepository,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val objectMapperBehandlingEvents = ObjectMapper().registerModule(JavaTimeModule()).configure(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false
        )
    }

    @Transactional
    fun cleanupMergedDocuments() {
        mergedDocumentRepository.deleteByCreatedBefore(LocalDateTime.now().minusWeeks(3))
    }

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun cleanupAfterBehandling(behandlingChangedEvent: BehandlingChangedEvent) {
        val behandling = behandlingChangedEvent.behandling

        if (behandling.ferdigstilling?.avsluttet != null) {
            logger.debug("Received behandlingEndretEvent for avsluttet behandling. Deleting meldinger and sattPaaVent.")

            if (behandling.sattPaaVent != null) {
                try {
                    behandlingService.setSattPaaVent(
                        behandlingId = behandling.id,
                        utfoerendeSaksbehandlerIdent = systembrukerIdent,
                        systemUserContext = true,
                        input = null,
                    )
                } catch (e: Exception) {
                    logger.error("couldn't cleanup sattPaaVent", e)
                }
            }

            meldingRepository.findByBehandlingIdOrderByCreatedDesc(behandlingId = behandling.id)
                .forEach { melding ->
                    try {
                        meldingRepository.delete(melding)
                    } catch (exception: Exception) {
                        logger.error("Could not delete melding with id ${melding.id}", exception)
                    }
                }
        } else if (behandlingChangedEvent.changeList.any { it.felt == BehandlingChangedEvent.Felt.FEILREGISTRERING } && behandling.feilregistrering != null) {
            logger.debug(
                "Cleanup and notifying vedtaksinstans after feilregistrering. Behandling.id: {}",
                behandling.id
            )
            deleteDokumenterUnderBehandling(behandling)
            deleteFromKaka(behandling)

            if (behandling.shouldUpdateInfotrygd()) {
                logger.debug("Feilregistrering av behandling skal registreres i Infotrygd.")
                fssProxyClient.setToFeilregistrertInKabal(
                    sakId = behandling.kildeReferanse,
                    input = FeilregistrertInKabalInput(
                        saksbehandlerIdent = behandlingChangedEvent.changeList.first().saksbehandlerident!!,
                    )
                )
                logger.debug("Feilregistrering av behandling ble registrert i Infotrygd.")
            }

            if (!behandling.gosysOppgaveRequired) {
                notifyVedtaksinstansThroughKafka(behandling)
            }
        }
    }

    private fun deleteDokumenterUnderBehandling(behandling: Behandling) {
        dokumentUnderArbeidService.findDokumenterNotFinished(behandlingId = behandling.id, checkReadAccess = false).forEach {
            try {
                if (!it.erMarkertFerdig()) {
                    dokumentUnderArbeidService.slettDokument(
                        dokumentId = it.id,
                        innloggetIdent = behandling.feilregistrering!!.navIdent,
                    )
                } else {
                    //Don't delete since it's marked as finished, and is being sent.
                    //This will probably only happen during E2E-tests (which will delete the behandling anyway).
                }
            } catch (e: Exception) {
                //best effort
                logger.warn("Couldn't clean up dokumenter under arbeid", e)
            }
        }
    }

    private fun notifyVedtaksinstansThroughKafka(behandling: Behandling) {
        val behandlingEvent = BehandlingEvent(
            eventId = UUID.randomUUID(),
            kildeReferanse = behandling.kildeReferanse,
            kilde = behandling.fagsystem.navn,
            kabalReferanse = behandling.id.toString(),
            type = BehandlingEventType.BEHANDLING_FEILREGISTRERT,
            detaljer = BehandlingDetaljer(
                behandlingFeilregistrert =
                BehandlingFeilregistrertDetaljer(
                    feilregistrert = behandling.feilregistrering!!.registered,
                    navIdent = behandling.feilregistrering!!.navIdent,
                    reason = behandling.feilregistrering!!.reason,
                    type = behandling.type,
                )
            )
        )
        kafkaEventRepository.save(
            KafkaEvent(
                id = UUID.randomUUID(),
                behandlingId = behandling.id,
                kilde = behandling.fagsystem.navn,
                kildeReferanse = behandling.kildeReferanse,
                jsonPayload = objectMapperBehandlingEvents.writeValueAsString(behandlingEvent),
                type = EventType.BEHANDLING_EVENT
            )
        )
    }

    private fun deleteFromKaka(behandling: Behandling) {
        when (behandling) {
            is Klagebehandling -> {
                when (behandling.kakaKvalitetsvurderingVersion) {
                    2 -> {
                        kakaApiGateway.deleteKvalitetsvurderingV2(behandling.kakaKvalitetsvurderingId!!)
                        behandling.kakaKvalitetsvurderingId = null
                        klagebehandlingRepository.save(behandling)
                    }
                }
            }

            is Ankebehandling -> {
                when (behandling.kakaKvalitetsvurderingVersion) {
                    2 -> {
                        kakaApiGateway.deleteKvalitetsvurderingV2(behandling.kakaKvalitetsvurderingId!!)
                        behandling.kakaKvalitetsvurderingId = null
                        ankebehandlingRepository.save(behandling)
                    }
                }
            }

            is Omgjoeringskravbehandling -> {
                {} //Do nothing
            }

            is AnkeITrygderettenbehandling -> {
                {} //Do nothing
            }

            is BehandlingEtterTrygderettenOpphevet -> {
                {} //Do nothing
            }

        }
    }
}