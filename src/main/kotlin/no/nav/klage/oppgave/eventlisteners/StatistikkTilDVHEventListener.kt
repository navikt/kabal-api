package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.oppgave.domain.events.KlagebehandlingEndretEvent
import no.nav.klage.oppgave.service.StatistikkTilDVHService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class StatistikkTilDVHEventListener(private val statistikkTilDVHService: StatistikkTilDVHService) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Async
    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun klagebehandlingEndretEventToDVH(klagebehandlingEndretEvent: KlagebehandlingEndretEvent) {
        logger.debug("Received KlagebehandlingEndretEvent for klagebehandlingId ${klagebehandlingEndretEvent.klagebehandling.id} in StatistikkTilDVHEventListener")
        statistikkTilDVHService.process(
            klagebehandlingEndretEvent.klagebehandling,
            klagebehandlingEndretEvent.endringslogginnslag
        )
    }
}