package no.nav.klage.oppgave.clients.nom

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.lang.System.currentTimeMillis


@Component
class NomClient(
    private val nomWebClient: WebClient,
    private val tokenUtil: TokenUtil
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun <T> runWithTiming(block: () -> T): T {
        val start = currentTimeMillis()
        try {
            return block.invoke()
        } finally {
            val end = currentTimeMillis()
            logger.debug("Time it took to call nom: ${end - start} millis")
        }
    }

    @Retryable
    fun getAnsatt(navIdent: String): GetAnsattResponse {
        return runWithTiming {
            val query = getAnsattQuery(navIdent)
            logger.debug("query: {}", query)
            val token = tokenUtil.getAppAccessTokenWithNomScope()
            try {
                nomWebClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .bodyValue(query)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError) { response ->
                        logErrorResponse(response, ::getAnsatt.name, secureLogger)
                    }
                    .bodyToMono<GetAnsattResponse>()
                    .block() ?: throw RuntimeException("Ansatt not found")
            } catch (e: Exception) {
                secureLogger.error("Could not get ansatt info for navIdent $navIdent", e)
                throw e
            }
        }
    }
}