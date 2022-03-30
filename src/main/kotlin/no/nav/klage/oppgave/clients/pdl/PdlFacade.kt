package no.nav.klage.oppgave.clients.pdl

import no.nav.klage.oppgave.clients.pdl.graphql.HentPersonMapper
import no.nav.klage.oppgave.clients.pdl.graphql.HentPersonResponse
import no.nav.klage.oppgave.clients.pdl.graphql.PdlClient
import no.nav.klage.oppgave.clients.pdl.graphql.PdlPerson
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
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
        private val secureLogger = getSecureLogger()
    }

    fun getPersonInfo(fnr: String): Person {
        if (personCacheService.isCached(fnr)) {
            return personCacheService.getPerson(fnr)
        }
        val hentPersonResponse: HentPersonResponse = pdlClient.getPersonInfo(fnr)
        val pdlPerson = hentPersonResponse.getPersonOrLogErrors(fnr)
        return hentPersonMapper.mapToPerson(fnr, pdlPerson).also { personCacheService.updatePersonCache(it) }
    }

    fun personExists(fnr: String): Boolean {
        try {
            getPersonInfo(fnr)
        } catch (e: Exception) {
            return false
        }
        return true
    }

    private fun HentPersonResponse.getPersonOrLogErrors(fnr: String): PdlPerson =
        if (this.errors.isNullOrEmpty() && this.data != null && this.data.hentPerson != null) {
            this.data.hentPerson
        } else {
            logger.warn("Errors returned from PDL or person not found. See securelogs for details.")
            secureLogger.warn("Errors returned for hentPerson($fnr) from PDL: ${this.errors}")
            throw RuntimeException("Klarte ikke å hente person fra PDL")
        }
}