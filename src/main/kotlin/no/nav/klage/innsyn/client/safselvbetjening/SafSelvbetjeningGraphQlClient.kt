package no.nav.klage.innsyn.client.safselvbetjening

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.slf4j.Logger
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Component
class SafSelvbetjeningGraphQlClient(
    private val safSelvbetjeningWebClient: WebClient,
    private val tokenUtil: TokenUtil
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Retryable
    fun getJournalpostById(
        journalpostId: String,
    ): GetJournalpostByIdResponse {
        val response = runWithTimingAndLogging {
            safSelvbetjeningWebClient.post()
                .uri("graphql")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${tokenUtil.getOnBehalfOfTokenWithSafSelvbetjeningScope()}"
                )
                .bodyValue(getJournalpostByIdQuery(journalpostId = journalpostId))
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    logErrorResponse(response, ::getJournalpostById.name, secureLogger)
                }
                .bodyToMono<GetJournalpostByIdResponse>()
                .block() ?: throw RuntimeException("No connection to safselvbetjening")
        }

        return response
    }

    fun <T> runWithTimingAndLogging(block: () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return block.invoke()
        } finally {
            val end = System.currentTimeMillis()
            logger.debug("Time it took to call saf: ${end - start} millis")
        }
    }

    fun logErrorResponse(response: ClientResponse, functionName: String, logger: Logger): Mono<RuntimeException> {
        return response.bodyToMono(String::class.java).map {
            val errorString =
                "Got ${response.statusCode()} when requesting $functionName - response body: '$it'"
            logger.error(errorString)
            RuntimeException(errorString)
        }
    }

}