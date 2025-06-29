package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
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

    @EventListener
    fun handleMinsideMicrofrontendEvent(behandlingChangedEvent: BehandlingChangedEvent) {
        logger.debug(
            "Received BehandlingEndretEvent for behandlingId {} in {}",
            behandlingChangedEvent.behandling.id,
            ::handleMinsideMicrofrontendEvent.name
        )
        minsideMicrofrontendService.process(behandlingChangedEvent)
        logger.debug("Processed BehandlingEndretEvent for behandlingId {}", behandlingChangedEvent.behandling.id)
    }
}