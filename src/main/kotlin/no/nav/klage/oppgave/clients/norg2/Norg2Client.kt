package no.nav.klage.oppgave.clients.norg2

import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration.Companion.ENHET_CACHE
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpStatusCode
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class Norg2Client(private val norg2WebClient: WebClient) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Retryable
    @Cacheable(ENHET_CACHE)
    fun fetchEnhet(enhetNr: String): Enhet {
        return try {
            norg2WebClient.get()
                .uri("/enhet/{enhetNr}", enhetNr)
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    logErrorResponse(response, ::fetchEnhet.name, secureLogger)
                }
                .bodyToMono<EnhetResponse>()
                .block()
                ?.asEnhet() ?: throw RuntimeException("No enhet returned for enhetNr $enhetNr")
        } catch (ex: Exception) {
            val errorMessage = "Problems with getting enhet $enhetNr from Norg2"
            logger.error(errorMessage, ex)
            throw RuntimeException(errorMessage, ex)
        }
    }

}
