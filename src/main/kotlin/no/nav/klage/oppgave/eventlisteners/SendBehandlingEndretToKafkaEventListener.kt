package no.nav.klage.oppgave.eventlisteners

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.service.BehandlingEndretKafkaProducer
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.hibernate.Hibernate
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class SendBehandlingEndretToKafkaEventListener(
    private val behandlingEndretKafkaProducer: BehandlingEndretKafkaProducer,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        val objectMapper: ObjectMapper = ourJacksonObjectMapper()
    }

    /* Denne kjøres utenfor transaksjonen. Trenger man at dette kjøres i en transaksjon, kan man bruke @Transactional(propagation = Propagation.REQUIRES_NEW)  eller en kombinasjon av @Transactional og @Async */
    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun indexKlagebehandling(behandlingEndretEvent: BehandlingEndretEvent) {
        logger.debug("Received BehandlingEndretEvent for behandlingId {}", behandlingEndretEvent.behandling.id)
        val behandling = Hibernate.unproxy(behandlingEndretEvent.behandling) as Behandling
        try {
            when (behandling.type) {
                Type.KLAGE ->
                    behandlingEndretKafkaProducer.sendKlageEndretV2(behandling as Klagebehandling)
                Type.ANKE ->
                    behandlingEndretKafkaProducer.sendAnkeEndretV2(behandling as Ankebehandling)
                Type.ANKE_I_TRYGDERETTEN ->
                    behandlingEndretKafkaProducer.sendAnkeITrygderettenEndretV2(behandling as AnkeITrygderettenbehandling)
            }
        } catch (e: Exception) {
            logger.error("could not index behandling with id ${behandlingEndretEvent.behandling.id}", e)
        }
    }
}