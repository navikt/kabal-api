package no.nav.klage.oppgave.clients.tilgangsmaskinen

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono


@Component
class TilgangsmaskinenRestClient(
    private val tilgangsmaskinenWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Retryable
    fun getTilgangsmaskinenErrorResponse(
        /** fnr, dnr or aktorId */
        brukerId: String,
        navIdent: String?
    ): TilgangsmaskinenErrorResponse? {
        return runWithTimingAndLogging {
            val uri: String
            val token: String
            if (navIdent != null) {
                uri = "/api/v1/ccf/komplett/$navIdent"
                token = "Bearer ${tokenUtil.getAppAccessTokenWithTilgangsmaskinenScope()}"
            } else {
                uri = "/api/v1/komplett"
                token = "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithTilgangsmaskinenScope()}"
            }

            tilgangsmaskinenWebClient.post()
                .uri(uri)
                .bodyValue(brukerId)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    token,
                )
                .retrieve()
                .onStatus({ it.value() == HttpStatus.FORBIDDEN.value() }) { response ->
                    response.bodyToMono(TilgangsmaskinenErrorResponse::class.java)
                        .flatMap { errorResponse ->
                            Mono.error(TilgangsmaskinenException(errorResponse))
                        }
                }
                .toBodilessEntity()
                .mapNotNull<TilgangsmaskinenErrorResponse?> { null }
                .onErrorResume(TilgangsmaskinenException::class.java) { ex ->
                    Mono.just(ex.errorResponse)
                }
                .block()
        }
    }

    fun <T> runWithTimingAndLogging(block: () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return block.invoke()
        } finally {
            val end = System.currentTimeMillis()
            logger.debug("Time it took to call tilgangsmaskinen: ${end - start} millis")
        }
    }

    private class TilgangsmaskinenException(val errorResponse: TilgangsmaskinenErrorResponse) : RuntimeException()
}
