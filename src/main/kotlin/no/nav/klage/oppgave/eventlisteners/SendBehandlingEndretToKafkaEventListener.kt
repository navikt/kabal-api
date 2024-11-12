package no.nav.klage.oppgave.eventlisteners

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.service.BehandlingEndretKafkaProducer
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class SendBehandlingEndretToKafkaEventListener(
    private val behandlingEndretKafkaProducer: BehandlingEndretKafkaProducer,
    private val behandlingRepository: BehandlingRepository,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        val objectMapper: ObjectMapper = ourJacksonObjectMapper()
    }

    /* This code needs a transaction b/c of lazy loading */
    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun indexKlagebehandling(behandlingEndretEvent: BehandlingEndretEvent) {
        logger.debug("Received BehandlingEndretEvent for behandlingId {}", behandlingEndretEvent.behandling.id)
        //full fetch to make sure all collections are loaded
        val behandling = behandlingRepository.findByIdEager(behandlingEndretEvent.behandling.id)
        try {
            when (behandling) {
                is Klagebehandling ->
                    behandlingEndretKafkaProducer.sendKlageEndret(behandling)

                is Ankebehandling ->
                    behandlingEndretKafkaProducer.sendAnkeEndret(behandling)

                is AnkeITrygderettenbehandling ->
                    behandlingEndretKafkaProducer.sendAnkeITrygderettenEndret(behandling)

                is BehandlingEtterTrygderettenOpphevet ->
                    behandlingEndretKafkaProducer.sendBehandlingOpprettetEtterTrygderettenOpphevet(behandling)

                is Omgjoeringskravbehandling ->
                    behandlingEndretKafkaProducer.sendOmgjoeringskravEndret(behandling)
            }
        } catch (e: Exception) {
            logger.error("could not index behandling with id ${behandlingEndretEvent.behandling.id}", e)
        }
        logger.debug("Processed BehandlingEndretEvent for behandlingId {}", behandlingEndretEvent.behandling.id)
    }
}