package no.nav.klage.oppgave.clients.kant

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
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

    @Retryable
    fun getValidationErrors(kantId: UUID): KantValidationErrors =
        kantApiWebClient
            .get()
            .uri("/api/{kantId}/validationErrors", kantId)
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKantApiScope()}",
            ).retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logger.error("Kant request failed with HTTP status {}", response.statusCode().value())
                response.releaseBody().thenReturn(KantApiException(response.statusCode().value()))
            }.bodyToMono<KantValidationErrors>()
            .block() ?: throw RuntimeException("Validation errors could not be retrieved")

    @Retryable
    fun createSaksdata(input: KantSaksdataInput): KantSaksdata =
        kantApiWebClient
            .post()
            .uri("/api/saksdata")
            .bodyValue(input)
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKantApiScope()}",
            ).retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logger.error("Kant request failed with HTTP status {}", response.statusCode().value())
                response.releaseBody().thenReturn(KantApiException(response.statusCode().value()))
            }.bodyToMono<KantSaksdata>()
            .block() ?: throw RuntimeException("Error when creating saksdata in kant-api")

    @Retryable
    fun finalizeSaksdata(
        id: UUID,
        input: KantAvslutningInput,
    ): KantSaksdata =
        kantApiWebClient
            .post()
            .uri("/api/saksdata/{id}/finalize", id)
            .bodyValue(input)
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKantApiScope()}",
            ).retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logger.error("Kant request failed with HTTP status {}", response.statusCode().value())
                response.releaseBody().thenReturn(KantApiException(response.statusCode().value()))
            }.bodyToMono<KantSaksdata>()
            .block() ?: throw RuntimeException("Error when finalizing saksdata in kant-api")
}

class KantApiException(
    val status: Int,
) : RuntimeException("Kant request failed with HTTP status $status")
