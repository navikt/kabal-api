package no.nav.klage.oppgave.clients.skjermede

import io.micrometer.tracing.Tracer
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class SkjermedeApiClient(
    private val skjermedeWebClient: WebClient,
    private val tokenUtil: TokenUtil,
    private val tracer: Tracer
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun isSkjermet(fnr: String): Boolean {
        logger.debug("Calling is skjermet")
        secureLogger.debug("Calling is skjermet with fnr {}", fnr)
        return skjermedeWebClient.post()
            .uri { it.path("/skjermet").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSkjermedeAccessToken()}"
            )
            .bodyValue(SkjermetRequest(personident = fnr))
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::isSkjermet.name, secureLogger)
            }
            .bodyToMono<Boolean>()
            .block() ?: throw RuntimeException("Null response from skjermede")
    }

    private data class SkjermetRequest(
        val personident: String
    )
}