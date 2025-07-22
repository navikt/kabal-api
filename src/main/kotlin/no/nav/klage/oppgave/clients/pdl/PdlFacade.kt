package no.nav.klage.oppgave.clients.pdl

import no.nav.klage.oppgave.clients.pdl.graphql.*
import no.nav.klage.oppgave.exceptions.PDLErrorException
import no.nav.klage.oppgave.exceptions.PDLPersonNotFoundException
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.stereotype.Component

@Component
class PdlFacade(
    private val pdlClient: PdlClient,
    private val personCacheService: PersonCacheService,
    private val hentPersonMapper: HentPersonMapper
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    fun getPersonInfo(fnr: String): Person {
        if (personCacheService.isCached(fnr)) {
            logger.debug("Returning person from cache.")
            return personCacheService.getPerson(fnr)
        }
        logger.debug("Person not found in cache, fetching from PDL.")
        val hentPersonResponse: HentPersonResponse = pdlClient.getPersonInfo(fnr)
        val pdlPerson = hentPersonResponse.getPersonOrThrowError(fnr)
        return hentPersonMapper.mapToPerson(fnr, pdlPerson).also { personCacheService.updatePersonCache(it) }
    }

    fun fillPersonCache(fnrList: List<String>) {
        val fnrsNotInPersonCache = personCacheService.findFnrsNotInCache(fnrList)
        val pdlOutput = pdlClient.getPersonBulk(fnrList = fnrsNotInPersonCache)
        pdlOutput.data?.hentPersonBolk?.forEach { hentPersonBolkResult ->
            val pdlPerson = hentPersonBolkResult.person
            if (pdlPerson != null) {
                try {
                    personCacheService.updatePersonCache(hentPersonMapper.mapToPerson(hentPersonBolkResult.ident, pdlPerson))
                } catch (e: Exception) {
                    teamLogger.error("Error while mapping person with fnr ${hentPersonBolkResult.ident} from PDL", e)
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

    fun getFoedselsnummerFromIdent(ident: String): String {
        val query = hentFolkeregisterIdentQuery(ident = ident)
        return getIdent(query = query)
    }

    fun getAktorIdFromIdent(ident: String): String {
        val query = hentAktorIdQuery(ident = ident)
        return getIdent(query = query)
    }

    private fun HentPersonResponse.getPersonOrThrowError(fnr: String): PdlPerson =
        if (this.errors.isNullOrEmpty() && this.data != null && this.data.hentPerson != null) {
            this.data.hentPerson
        } else {
            logger.warn("Errors returned from PDL or person not found. See team-logs for details.")
            teamLogger.warn("Errors returned for hentPerson($fnr) from PDL: ${this.errors}")
            if (this.errors?.any { it.extensions.code == "not_found" } == true) {
                throw PDLPersonNotFoundException("Fant ikke personen i PDL")
            }
            throw PDLErrorException("Klarte ikke å hente person fra PDL")
        }

    private fun getIdent(query: PersonGraphqlQuery): String {
        return pdlClient.getIdents(query = query).data?.hentIdenter?.identer?.firstOrNull()?.ident ?: throw PDLErrorException("Klarte ikke å hente person fra PDL")
    }
}