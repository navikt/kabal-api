package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.service.MinsideMicrofrontendService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class MinsideMicrofrontendEventListener(
    private val minsideMicrofrontendService: MinsideMicrofrontendService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun handleMinsideMicrofrontendEvent(behandlingEndretEvent: BehandlingEndretEvent) {
        logger.debug(
            "Received BehandlingEndretEvent for behandlingId {} in {}",
            behandlingEndretEvent.behandling.id,
            ::handleMinsideMicrofrontendEvent.name
        )
        minsideMicrofrontendService.process(behandlingEndretEvent)
        logger.debug("Processed BehandlingEndretEvent for behandlingId {}", behandlingEndretEvent.behandling.id)
    }
}