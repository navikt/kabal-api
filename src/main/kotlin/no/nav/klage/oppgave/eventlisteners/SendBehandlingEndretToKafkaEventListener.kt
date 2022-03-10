package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.service.BehandlingEndretKafkaProducer
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class SendBehandlingEndretToKafkaEventListener(private val behandlingEndretKafkaProducer: BehandlingEndretKafkaProducer) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    /* Denne kjøres utenfor transaksjonen. Trenger man at dette kjøres i en transaksjon, kan man bruke @Transactional(propagation = Propagation.REQUIRES_NEW)  eller en kombinasjon av @Transactional og @Async */
    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun indexKlagebehandling(behandlingEndretEvent: BehandlingEndretEvent) {
        logger.debug("Received BehandlingEndretEvent for behandlingId ${behandlingEndretEvent.behandling.id}")

        if (behandlingEndretEvent.behandling.type == Type.KLAGE) {
            behandlingEndretKafkaProducer.sendKlageEndretV2(behandlingEndretEvent.behandling as Klagebehandling)
        } else {
            behandlingEndretKafkaProducer.sendAnkeEndretV2(behandlingEndretEvent.behandling as Ankebehandling)
        }


    }
}