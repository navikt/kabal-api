package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.service.MinsideMicrofrontendService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class MinsideMicrofrontendEventListener(
    private val minsideMicrofrontendService: MinsideMicrofrontendService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }
//TODO: Uncomment this when ready for use
    @EventListener
    fun handleMinsideMicrofrontendEvent(behandlingEndretEvent: BehandlingEndretEvent) {
        logger.debug(
            "Received BehandlingEndretEvent for behandlingId {} in {}",
            behandlingEndretEvent.behandling.id,
            javaClass.enclosingClass.simpleName
        )
        minsideMicrofrontendService.process(behandlingEndretEvent)
        logger.debug("Processed BehandlingEndretEvent for behandlingId {}", behandlingEndretEvent.behandling.id)
    }
}