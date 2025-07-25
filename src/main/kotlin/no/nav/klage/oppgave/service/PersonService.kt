package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.pdl.PersonCacheService
import no.nav.klage.oppgave.domain.person.Person
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.service.mapper.toPerson
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.stereotype.Service
import java.net.InetAddress

@Service
class PersonService(
    private val personCacheService: PersonCacheService,
    private val pdlFacade: PdlFacade,
    private val behandlingService: BehandlingService,
    private val behandlingRepository: BehandlingRepository,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    fun getPersonInfo(fnr: String): Person {
        if (personCacheService.isCached(fnr)) {
            return personCacheService.getPerson(fnr)
        }
        val pdlPerson = pdlFacade.getPerson(fnr)
        return pdlPerson.toPerson(fnr).also { personCacheService.updatePersonCache(it) }
    }

    fun fillPersonCache(fnrList: List<String>) {
        val fnrsNotInPersonCache = personCacheService.findFnrsNotInCache(fnrList)
        fnrsNotInPersonCache.chunked(1000).forEach { fnrListChunk ->
            val pdlOutput = pdlFacade.getPersonBulk(fnrList = fnrListChunk)
            pdlOutput.forEach { hentPersonBolkResult ->
                val pdlPerson = hentPersonBolkResult.person
                if (pdlPerson != null) {
                    try {
                        personCacheService.updatePersonCache(hentPersonBolkResult.person.toPerson(fnr = hentPersonBolkResult.ident))
                    } catch (e: Exception) {
                        teamLogger.error("Error while mapping person with fnr ${hentPersonBolkResult.ident} from PDL", e)
                    }
                } else {
                    teamLogger.error("Missing pdlPerson for fnr ${hentPersonBolkResult.ident}. Code: ${hentPersonBolkResult.code}")
                }
            }
        }
    }

    fun personExists(fnr: String): Boolean {
        try {
            getPersonInfo(fnr)
        } catch (e: Exception) {
            return false
        }
        return true
    }

    fun refreshPersonInCache(fnr: String) {
        personCacheService.removePersonFromCache(foedselsnr = fnr)
        getPersonInfo(fnr)
    }

    fun fillCacheWithAllMissingPersons() {
        val start = System.currentTimeMillis()
        logger.debug("Finding all persons in behandlinger to fill cache in pod ${InetAddress.getLocalHost().hostName}")
        val allBehandlinger = behandlingRepository.findByFeilregistreringIsNull()
        logger.debug("Found all behandlinger: ${allBehandlinger.size}, took ${System.currentTimeMillis() - start} ms in pod ${InetAddress.getLocalHost().hostName}")

        val allSakenGjelderFnr = allBehandlinger.filter { it.sakenGjelder.partId.type == PartIdType.PERSON }
            .map { it.sakenGjelder.partId.value }
            .distinct()

        val allKlagerFnr = allBehandlinger.filter { it.klager.partId.type == PartIdType.PERSON }
            .map { it.klager.partId.value }
            .distinct()

        val allFullmektigFnr = allBehandlinger.filter { it.prosessfullmektig?.partId?.type == PartIdType.PERSON }
            .map { it.prosessfullmektig?.partId?.value }
            .distinct()

        val allPersonsInBehandlingerFnr = (allSakenGjelderFnr + allKlagerFnr + allFullmektigFnr).distinct().filterNotNull()

        logger.debug("Found all distinct persons: ${allPersonsInBehandlingerFnr.size}, took ${System.currentTimeMillis() - start} ms in pod ${InetAddress.getLocalHost().hostName}")

        fillPersonCache(allPersonsInBehandlingerFnr)

        logger.debug("Finished inserting all persons in cache in ${System.currentTimeMillis() - start} ms in pod ${InetAddress.getLocalHost().hostName}")
    }

}