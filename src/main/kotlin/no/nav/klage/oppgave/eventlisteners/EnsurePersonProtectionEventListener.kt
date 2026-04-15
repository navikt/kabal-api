package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Felt
import no.nav.klage.oppgave.service.PersonProtectionService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class EnsurePersonProtectionEventListener(
    private val personProtectionService: PersonProtectionService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        // When to consider event
        private val BEHANDLING_OPPRETTET_FIELDS = setOf(
            Felt.KLAGEBEHANDLING_OPPRETTET,
            Felt.ANKEBEHANDLING_OPPRETTET,
            Felt.OMGJOERINGSKRAVBEHANDLING_OPPRETTET,
            Felt.ANKE_I_TRYGDERETTEN_OPPRETTET,
            Felt.BEHANDLING_ETTER_TR_OPPHEVET_OPPRETTET,
            Felt.BEGJAERING_OM_GJENOPPTAKSBEHANDLING_OPPRETTET,
            Felt.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_OPPRETTET,
        )
    }

    @EventListener
    fun ensurePersonProtectionOnBehandlingCreated(behandlingChangedEvent: BehandlingChangedEvent) {
        val isBehandlingCreated = behandlingChangedEvent.changeList.any { it.felt in BEHANDLING_OPPRETTET_FIELDS }

        if (!isBehandlingCreated) {
            return
        }

        val behandling = behandlingChangedEvent.behandling

        if (!behandling.sakenGjelder.erPerson()) {
            return
        }

        val foedselsnummer = behandling.sakenGjelder.partId.value

        logger.debug("Ensuring person protection exists for sakenGjelder in behandling {}", behandling.id)

        personProtectionService.createPersonProtection(foedselsnummer = foedselsnummer)
    }
}

