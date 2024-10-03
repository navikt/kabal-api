package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.service.BrukervarselService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class BrukervarselEventListener(
    private val brukervarselService: BrukervarselService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @EventListener
    fun behandlingEndretEventToBrukervarsel(behandlingEndretEvent: BehandlingEndretEvent) {
        logger.debug(
            "Received BehandlingEndretEvent for behandlingId {} in BrukervarselEventListener",
            behandlingEndretEvent.behandling.id
        )
        brukervarselService.process(behandlingEndretEvent)
        logger.debug("Processed BehandlingEndretEvent for behandlingId {} in BrukervarselEventListener", behandlingEndretEvent.behandling.id)
    }
}