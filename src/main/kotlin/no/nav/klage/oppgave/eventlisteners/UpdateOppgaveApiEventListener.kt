package no.nav.klage.oppgave.eventlisteners

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.Felt
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.service.OppgaveApiService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class UpdateOppgaveApiEventListener(
    private val behandlingRepository: BehandlingRepository,
    private val oppgaveApiService: OppgaveApiService,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
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
    fun updateOppgave(behandlingEndretEvent: BehandlingEndretEvent) {
        logger.debug("Received BehandlingEndretEvent for behandlingId {}f", behandlingEndretEvent.behandling.id)
        val behandling = behandlingRepository.findByIdEager(behandlingEndretEvent.behandling.id)

        if (behandling.oppgaveId != null && behandlingEndretEvent.endringslogginnslag.any { it.felt == Felt.TILDELT_SAKSBEHANDLERIDENT }) {
            oppgaveApiService.assignOppgave(
                oppgaveId = behandling.oppgaveId!!,
                tildeltSaksbehandlerIdent = behandling.tildeling?.saksbehandlerident,
                systemContext = behandlingEndretEvent.endringslogginnslag.any { it.saksbehandlerident == systembrukerIdent }
            )
        }
    }
}