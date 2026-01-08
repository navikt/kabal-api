package no.nav.klage.innsyn.client.safselvbetjening

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class SafSelvbetjeningGraphQlClient(
    private val safSelvbetjeningWebClient: WebClient,
    private val tokenUtil: TokenUtil
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
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
                    logErrorResponse(
                        response = response,
                        functionName = ::getJournalpostById.name,
                        classLogger = logger,
                    )
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

}