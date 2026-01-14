package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.kaptein.service.KapteinService
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.service.BehandlingEndretKafkaProducer
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class SendBehandlingEndretToKafkaEventListener(
    private val behandlingEndretKafkaProducer: BehandlingEndretKafkaProducer,
    private val behandlingRepository: BehandlingRepository,
    private val kapteinService: KapteinService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun indexKlagebehandling(behandlingChangedEvent: BehandlingChangedEvent) {
        logger.debug("Received BehandlingEndretEvent for behandlingId {}", behandlingChangedEvent.behandling.id)
        //full fetch to make sure all collections are loaded
        val behandling = behandlingRepository.findByIdEager(behandlingChangedEvent.behandling.id)
        try {
            behandlingEndretKafkaProducer.sendBehandlingEndret(behandling)
        } catch (e: Exception) {
            logger.error("could not index behandling with id ${behandlingChangedEvent.behandling.id}", e)
        }
        try {
            kapteinService.sendBehandlingChanged(behandling)
        } catch (e: Exception) {
            logger.error("could not send behandling to Kaptein with id ${behandlingChangedEvent.behandling.id}", e)
        }
        logger.debug("Processed BehandlingEndretEvent for behandlingId {}", behandlingChangedEvent.behandling.id)
    }
}