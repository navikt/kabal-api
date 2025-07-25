package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.pdl.PersonCacheService
import no.nav.klage.oppgave.domain.person.Person
import no.nav.klage.oppgave.service.mapper.toPerson
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.stereotype.Service

@Service
class PersonService(
    private val personCacheService: PersonCacheService,
    private val pdlFacade: PdlFacade,
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
}