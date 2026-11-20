package no.nav.klage.oppgave.clients.kant

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.util.retry.Retry
import java.time.Duration
import java.util.UUID

@Component
class KantApiClient(
    private val kantApiWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getValidationErrors(kantId: UUID): KantValidationErrors =
        kantApiWebClient
            .get()
            .uri { it.path("/api/{$kantId}/validationErrors").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKantApiScope()}",
            ).retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logger.error("Kant request failed with HTTP status {}", response.statusCode().value())
                response.releaseBody().thenReturn(KantApiException(response.statusCode().value()))
            }.bodyToMono<KantValidationErrors>()
            .block() ?: throw RuntimeException("Validation errors could not be retrieved")

    fun createSaksdata(input: KantSaksdataInput): KantSaksdata =
        kantApiWebClient
            .post()
            .uri("/api/saksdata")
            .bodyValue(input)
            .exchangeSaksdata()

    fun finalizeSaksdata(
        id: UUID,
        input: KantAvslutningInput,
    ): KantSaksdata =
        kantApiWebClient
            .put()
            .uri("/api/saksdata/{id}/avslutning", id)
            .bodyValue(input)
            .exchangeSaksdata()

    private fun WebClient.RequestHeadersSpec<*>.exchangeSaksdata(): KantSaksdata =
        headers { it.setBearerAuth(tokenUtil.getAppAccessTokenWithKantApiScope()) }
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logger.error("Kant request failed with HTTP status {}", response.statusCode().value())
                response.releaseBody().thenReturn(KantApiException(response.statusCode().value()))
            }.bodyToMono<KantSaksdata>()
            .retryWhen(
                Retry
                    .backoff(2, Duration.ofMillis(200))
                    .filter { (it is WebClientRequestException) || ((it is KantApiException) && (it.status >= 500)) }
                    .onRetryExhaustedThrow { _, signal -> signal.failure() },
            ).block(Duration.ofSeconds(20))
            ?: error("Kant returned an empty Saksdata response")
}

class KantApiException(
    val status: Int,
) : RuntimeException("Kant request failed with HTTP status $status")
