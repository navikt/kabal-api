package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.repositories.EndringsloggRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class StoreEndringsloggEventListener(private val endringsloggRepository: EndringsloggRepository) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @EventListener
    fun storeEndringslogg(behandlingEndretEvent: BehandlingEndretEvent) {
        logger.debug("Received BehandlingEndretEvent for behandlingId ${behandlingEndretEvent.behandling.id} in StoreEndringsloggEventListener")
        if (behandlingEndretEvent.endringslogginnslag.isNotEmpty()) {
            endringsloggRepository.saveAll(behandlingEndretEvent.endringslogginnslag)
        }
    }
}