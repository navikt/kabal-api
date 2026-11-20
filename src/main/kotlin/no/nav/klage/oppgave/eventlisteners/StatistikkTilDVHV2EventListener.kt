package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.service.StatistikkTilDVHV2Service
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class StatistikkTilDVHV2EventListener(
    private val statistikkTilDVHV2Service: StatistikkTilDVHV2Service,
) {
    @EventListener
    fun behandlingEndretEventToDVHV2(behandlingChangedEvent: BehandlingChangedEvent) {
        statistikkTilDVHV2Service.process(behandlingChangedEvent)
    }
}
