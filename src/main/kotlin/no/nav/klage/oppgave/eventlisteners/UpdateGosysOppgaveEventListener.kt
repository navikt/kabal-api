package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.service.GosysOppgaveService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.ourJsonMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.json.JsonMapper

@Service
class UpdateGosysOppgaveEventListener(
    private val gosysOppgaveService: GosysOppgaveService,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        val jsonMapper: JsonMapper = ourJsonMapper()
    }

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun updateOppgave(behandlingChangedEvent: BehandlingChangedEvent) {
        logger.debug("updateOppgave called. Received BehandlingEndretEvent for behandlingId {}", behandlingChangedEvent.behandling.id)

        val relevantChange = behandlingChangedEvent.changeList.find { it.felt == BehandlingChangedEvent.Felt.TILDELT_SAKSBEHANDLERIDENT }

        if (relevantChange != null && behandlingChangedEvent.behandling.gosysOppgaveId != null) {
            gosysOppgaveService.assignGosysOppgave(
                gosysOppgaveId = behandlingChangedEvent.behandling.gosysOppgaveId!!,
                tildeltSaksbehandlerIdent = behandlingChangedEvent.behandling.tildeling?.saksbehandlerident,
                utfoerendeSaksbehandlerIdent = relevantChange.saksbehandlerident ?: systembrukerIdent,
                behandlingId = behandlingChangedEvent.behandling.id,
                throwExceptionIfFerdigstilt = false,
            )
        }

        logger.debug("Processed BehandlingEndretEvent for behandlingId {}", behandlingChangedEvent.behandling.id)
    }
}