package no.nav.klage.oppgave.clients.pdl

import no.nav.klage.oppgave.clients.pdl.graphql.*
import no.nav.klage.oppgave.exceptions.PDLErrorException
import no.nav.klage.oppgave.exceptions.PDLPersonNotFoundException
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.stereotype.Component

@Component
class PdlFacade(
    private val pdlClient: PdlClient
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
    fun getPerson(fnr: String): PdlPerson {
        val hentPersonResponse: HentPersonResponse = pdlClient.getPersonInfo(fnr)
        return hentPersonResponse.getPersonOrThrowError(fnr)
    }

    fun getPersonBulk(fnrList: List<String>): List<HentPersonBolkResult> {
        return pdlClient.getPersonBulk(fnrList = fnrList).getResultsOrLogError()
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

    private fun HentPersonBolkResponse.getResultsOrLogError(): List<HentPersonBolkResult> =
        if (this.errors.isNullOrEmpty() && this.data != null && this.data.hentPersonBolk != null) {
            this.data.hentPersonBolk
        } else {
            logger.error("Errors returned from PDL or person not found. See team-logs for details.")
            teamLogger.error("Errors returned for hentPersonBolk from PDL: ${this.errors}")
            emptyList()
        }

    private fun getIdent(query: PersonGraphqlQuery): String {
        return pdlClient.getIdents(query = query).data?.hentIdenter?.identer?.firstOrNull()?.ident
            ?: throw PDLErrorException("Klarte ikke å hente person fra PDL")
    }
}