package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.service.StatistikkTilDVHService
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
    fun behandlingEndretEventToDVH(behandlingChangedEvent: BehandlingChangedEvent) {
        logger.debug(
            "Received BehandlingEndretEvent for behandlingId {} in StatistikkTilDVHEventListener",
            behandlingChangedEvent.behandling.id
        )
        statistikkTilDVHService.process(behandlingChangedEvent)
        logger.debug("Processed BehandlingEndretEvent for behandlingId {}", behandlingChangedEvent.behandling.id)
    }
}