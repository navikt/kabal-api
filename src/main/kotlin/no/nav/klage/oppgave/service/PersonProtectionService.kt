package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.domain.PersonProtection
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.PersonProtectionRepository
import no.nav.klage.oppgave.repositories.SakPersongalleriRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.system.measureTimeMillis

@Service
@Transactional
class PersonProtectionService(
    private val personProtectionRepository: PersonProtectionRepository,
    private val behandlingRepository: BehandlingRepository,
    private val sakPersongalleriRepository: SakPersongalleriRepository,
    private val klageLookupGateway: KlageLookupGateway,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun handlePersonProtectionChanged(foedselsnummer: String) {
        logger.debug("Handling possible person protection change")

        val updated = updatePersonProtection(foedselsnummer = foedselsnummer)
        if (updated) {
            reindexAffectedBehandlinger(foedselsnummer = foedselsnummer)
        }
    }

    private fun updatePersonProtection(foedselsnummer: String): Boolean {
        val existingProtection = personProtectionRepository.findByFoedselsnummer(foedselsnummer)

        if (existingProtection == null) {
            // Not relevant if we don't know the person
            return false
        } else {
            logger.debug("Updating existing person protection")
        }

        val person = klageLookupGateway.getPerson(fnr = foedselsnummer)

        existingProtection.fortrolig = person.fortrolig
        existingProtection.strengtFortrolig = person.strengtFortrolig || person.strengtFortroligUtland
        existingProtection.skjermet = person.egenAnsatt
        logger.debug("Updated person protection")
        return true
    }

    fun createPersonProtection(foedselsnummer: String) {
        val existingProtection = personProtectionRepository.findByFoedselsnummer(foedselsnummer)

        if (existingProtection == null) {
            val person = klageLookupGateway.getPerson(fnr = foedselsnummer)
            personProtectionRepository.save(
                PersonProtection(
                    foedselsnummer = foedselsnummer,
                    fortrolig = person.fortrolig,
                    strengtFortrolig = person.strengtFortrolig || person.strengtFortroligUtland,
                    skjermet = person.egenAnsatt,
                )
            )
            logger.debug("Created person protection")
        } else {
            logger.debug("Person protection already exists, not creating")
        }
    }

    private fun reindexAffectedBehandlinger(foedselsnummer: String) {
        val elapsedMillis = measureTimeMillis {
            val behandlingerBySakenGjelder = behandlingRepository.findBySakenGjelderPartIdValue(foedselsnummer)

            val persongalleriEntries = sakPersongalleriRepository.findByFoedselsnummer(foedselsnummer)
            val behandlingerByPersongalleri = persongalleriEntries
                .map { it.fagsystem to it.fagsakId }
                .distinct()
                .flatMap { (fagsystem, fagsakId) ->
                    behandlingRepository.findByFagsystemAndFagsakIdAndFeilregistreringIsNull(
                        fagsystem = fagsystem,
                        fagsakId = fagsakId,
                    )
                }

            val affectedBehandlinger = (behandlingerBySakenGjelder + behandlingerByPersongalleri)
                .distinctBy { it.id }

            logger.debug("Found {} behandlinger to reindex for person protection change", affectedBehandlinger.size)

            affectedBehandlinger.forEach { behandling ->
                applicationEventPublisher.publishEvent(
                    BehandlingChangedEvent(
                        behandling = behandling,
                        changeList = listOf(
                            Change(
                                saksbehandlerident = null,
                                felt = BehandlingChangedEvent.Felt.PERSON_PROTECTION_CHANGED,
                                behandlingId = behandling.id,
                            )
                        )
                    )
                )
            }
        }
        logger.debug("PersonProtectionService.reindexAffectedBehandlinger took {} ms", elapsedMillis)
    }
}