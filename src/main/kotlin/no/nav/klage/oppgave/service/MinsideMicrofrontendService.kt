package no.nav.klage.oppgave.service


import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.tms.microfrontend.MicrofrontendMessageBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class MinsideMicrofrontendService(
    private val behandlingRepository: BehandlingRepository,
    private val kafkaEventRepository: KafkaEventRepository
) {

    @Value("\${spring.application.name}")
    lateinit var applicationName: String

    @Value("\${MINE_KLAGER_MICROFRONTEND_ID}")
    lateinit var mineKlagerMicrofrontendId: String

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun process(behandlingChangedEvent: BehandlingChangedEvent) {
        logger.debug(
            "Received BehandlingEndretEvent for behandlingId {} in {}",
            behandlingChangedEvent.behandling.id,
            "MinsideMicrofrontendService"
        )
        if (behandlingChangedEvent.changeList.any {
                it.felt in listOf(
                    BehandlingChangedEvent.Felt.KLAGEBEHANDLING_MOTTATT,
                    BehandlingChangedEvent.Felt.ANKEBEHANDLING_MOTTATT,
                    BehandlingChangedEvent.Felt.OMGJOERINGSKRAVBEHANDLING_MOTTATT,
                    BehandlingChangedEvent.Felt.ANKE_I_TRYGDERETTEN_OPPRETTET,
                )
            }) {
            enableMinsideMicrofrontend(behandling = behandlingChangedEvent.behandling)
        } else if (behandlingChangedEvent.changeList.any {
                it.felt in listOf(
                    BehandlingChangedEvent.Felt.FEILREGISTRERING
                )
            }) {
            val behandling = behandlingChangedEvent.behandling
            val allBehandlinger =
                behandlingRepository.findBySakenGjelderPartIdValueAndFeilregistreringIsNull(behandling.sakenGjelder.partId.value)
            if (allBehandlinger.any {
                    it.id != behandling.id
                }
            ) {
                logger.debug("User has other existing behandling. Disabling not needed. Returning.")
            } else {
                logger.debug("User has no other existing behandling. Sending disable.")
                disableMinsideMicrofrontend(
                    behandling = behandling
                )
            }
        }
    }

    fun enableMinsideMicrofrontend(behandling: Behandling) {
        logger.debug("Enabling minside microfrontend for behandling with id {}", behandling.id)
        val payload = MicrofrontendMessageBuilder.enable {
            ident = behandling.sakenGjelder.partId.value
            microfrontendId = mineKlagerMicrofrontendId
            initiatedBy = applicationName
        }.text()
        saveKafkaEventPayload(
            behandling = behandling,
            payload = payload
        )
    }

    fun disableMinsideMicrofrontend(behandling: Behandling) {
        logger.debug("Disabling minside microfrontend for behandling with id {}", behandling.id)
        val payload = MicrofrontendMessageBuilder.disable {
            ident = behandling.sakenGjelder.partId.value
            microfrontendId = mineKlagerMicrofrontendId
            initiatedBy = applicationName
        }.text()
        saveKafkaEventPayload(
            behandling = behandling,
            payload = payload
        )
    }

    fun saveKafkaEventPayload(
        behandling: Behandling,
        payload: String
    ) {
        val eventId = UUID.randomUUID()
        kafkaEventRepository.save(
            KafkaEvent(
                id = eventId,
                behandlingId = behandling.id,
                kilde = behandling.fagsystem.navn,
                kildeReferanse = behandling.kildeReferanse,
                status = UtsendingStatus.IKKE_SENDT,
                jsonPayload = payload,
                type = EventType.MINSIDE_MICROFRONTEND_EVENT
            )
        )
    }
}