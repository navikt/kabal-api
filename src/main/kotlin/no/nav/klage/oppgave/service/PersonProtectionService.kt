package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.domain.PersonProtection
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.PersonProtectionRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PersonProtectionService(
    private val personProtectionRepository: PersonProtectionRepository,
    private val behandlingRepository: BehandlingRepository,
    private val klageLookupGateway: KlageLookupGateway,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun handlePersonProtectionChanged(foedselsnummer: String) {
        logger.debug("Handling person protection change")

        updatePersonProtection(foedselsnummer = foedselsnummer)

        reindexAffectedBehandlinger(foedselsnummer = foedselsnummer)
    }

    private fun updatePersonProtection(foedselsnummer: String) {
        val person = klageLookupGateway.getPerson(fnr = foedselsnummer)

        val existingProtection = personProtectionRepository.findByFoedselsnummer(foedselsnummer)

        if (existingProtection != null) {
            existingProtection.fortrolig = person.fortrolig
            existingProtection.strengtFortrolig = person.strengtFortrolig || person.strengtFortroligUtland
            existingProtection.skjermet = person.egenAnsatt
            logger.debug("Updated person protection")
        } else {
            personProtectionRepository.save(
                PersonProtection(
                    foedselsnummer = foedselsnummer,
                    fortrolig = person.fortrolig,
                    strengtFortrolig = person.strengtFortrolig || person.strengtFortroligUtland,
                    skjermet = person.egenAnsatt,
                )
            )
            logger.debug("Created person protection")
        }
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
        val affectedBehandlinger = behandlingRepository.findBySakenGjelderPartIdValue(foedselsnummer)

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
}