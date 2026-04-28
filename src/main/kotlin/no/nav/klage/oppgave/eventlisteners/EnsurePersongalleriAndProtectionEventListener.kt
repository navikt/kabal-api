package no.nav.klage.oppgave.eventlisteners

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.clients.klagelookup.Sak
import no.nav.klage.oppgave.domain.SakPersongalleri
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Felt
import no.nav.klage.oppgave.repositories.SakPersongalleriRepository
import no.nav.klage.oppgave.service.PersonProtectionService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class EnsurePersongalleriAndProtectionEventListener(
    private val personProtectionService: PersonProtectionService,
    private val sakPersongalleriRepository: SakPersongalleriRepository,
    private val klageLookupGateway: KlageLookupGateway,
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
    fun ensurePersongalleriAndProtectionOnBehandlingCreated(behandlingChangedEvent: BehandlingChangedEvent) {
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

        if (behandling.fagsystem == Fagsystem.FS36) {
            logger.debug("Fagsystem is FS36, so populating persongalleri and belonging person protection.")
            populatePersongalleri(behandling).minus(foedselsnummer).forEach { fnr ->
                personProtectionService.createPersonProtection(foedselsnummer = fnr)
            }
        }
    }

    private fun populatePersongalleri(behandling: Behandling): List<String> {
        val existingEntries = sakPersongalleriRepository.findByFagsystemAndFagsakId(
            fagsystem = behandling.fagsystem,
            fagsakId = behandling.fagsakId,
        )

        if (existingEntries.isNotEmpty()) {
            logger.debug("Persongalleri already exists for fagsystem {} and fagsakId {}, skipping", behandling.fagsystem, behandling.fagsakId)
            return emptyList()
        }

        val sak = Sak(
            sakId = behandling.fagsakId,
            ytelse = behandling.ytelse,
            fagsystem = behandling.fagsystem,
        )

        val foedselsnummerList = klageLookupGateway.getPersongalleri(sak)

        foedselsnummerList.forEach { foedselsnummer ->
            sakPersongalleriRepository.save(
                SakPersongalleri(
                    fagsystem = behandling.fagsystem,
                    fagsakId = behandling.fagsakId,
                    foedselsnummer = foedselsnummer,
                )
            )
        }

        logger.debug("Populated persongalleri for klagebehandling {}", behandling.id)
        return foedselsnummerList
    }
}