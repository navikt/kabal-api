package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class StatistikkTilDVHEventListener(private val statistikkTilDVHService: StatistikkTilDVHService) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @EventListener
    fun klagebehandlingEndretEventToDVH(behandlingEndretEvent: BehandlingEndretEvent) {
        logger.debug("Received BehandlingEndretEvent for behandlingId ${behandlingEndretEvent.behandling.id} in StatistikkTilDVHEventListener")
        statistikkTilDVHService.process(behandlingEndretEvent)
    }
}