package no.nav.klage.oppgave.service

import jakarta.transaction.Transactional
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.domain.klage.Felt
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.tms.microfrontend.MicrofrontendMessageBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

@Service
@Transactional
class MinsideMicrofrontendService(
    private val behandlingRepository: BehandlingRepository,
    private val kafkaEventRepository: KafkaEventRepository
) {

    @Value("\${spring.application.name}")
    lateinit var applicationName: String

    //TODO: Get correct microfrontendId
    private val microfrontendName = "mine-klager-microfrontend"

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun process(behandlingEndretEvent: BehandlingEndretEvent) {
        logger.debug(
            "Received BehandlingEndretEvent for behandlingId {} in {}",
            behandlingEndretEvent.behandling.id,
            "MinsideMicrofrontendService"
        )
        val payload = if (behandlingEndretEvent.endringslogginnslag.any {
                it.felt in listOf(
                    Felt.KLAGEBEHANDLING_MOTTATT,
                    Felt.ANKEBEHANDLING_MOTTATT,
                    Felt.OMGJOERINGSKRAVBEHANDLING_MOTTATT,
                    Felt.ANKE_I_TRYGDERETTEN_OPPRETTET,
                )
            }) {
            val behandling = behandlingEndretEvent.behandling
            val allBehandlinger =
                behandlingRepository.findBySakenGjelderPartIdValueAndFeilregistreringIsNull(behandling.sakenGjelder.partId.value)
            if (allBehandlinger.any {
                    it.id != behandling.id
                }
            ) {
                logger.debug("User has existing behandling. Assuming Minside microfrontend already is enabled. Returning.")
                null
            } else {
                logger.debug("User has no previous existing behandling. Sending enable.")
                MicrofrontendMessageBuilder.enable {
                    ident = behandling.sakenGjelder.partId.value
                    microfrontendId = microfrontendName
                    initiatedBy = applicationName
                }.text()
            }
        } else if (behandlingEndretEvent.endringslogginnslag.any {
                it.felt in listOf(
                    Felt.FEILREGISTRERING
                )
            }) {
            val behandling = behandlingEndretEvent.behandling
            val allBehandlinger =
                behandlingRepository.findBySakenGjelderPartIdValueAndFeilregistreringIsNull(behandling.sakenGjelder.partId.value)
            if (allBehandlinger.any {
                    it.id != behandling.id
                }
            ) {
                logger.debug("User has other existing behandling. Disabling not needed. Returning.")
                null
            } else {
                logger.debug("User has no other existing behandling. Sending disable.")
                MicrofrontendMessageBuilder.disable {
                    ident = behandling.sakenGjelder.partId.value
                    microfrontendId = microfrontendName
                    initiatedBy = applicationName
                }.text()
            }
        } else null

        if (payload != null) {
            logger.debug("Sending payload to Minside microfrontend: $payload")
            val eventId = UUID.randomUUID()
            kafkaEventRepository.save(
                KafkaEvent(
                    id = eventId,
                    behandlingId = behandlingEndretEvent.behandling.id,
                    kilde = behandlingEndretEvent.behandling.fagsystem.navn,
                    kildeReferanse = behandlingEndretEvent.behandling.kildeReferanse,
                    status = UtsendingStatus.IKKE_SENDT,
                    jsonPayload = payload,
                    type = EventType.MINSIDE_MICROFRONTEND_EVENT
                )
            )
        }
    }
}