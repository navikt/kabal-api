package no.nav.klage.oppgave.clients.kaka

import no.nav.klage.oppgave.clients.kaka.model.request.SaksdataInput
import no.nav.klage.oppgave.clients.kaka.model.response.KakaOutput
import no.nav.klage.oppgave.clients.kaka.model.response.ValidationErrors
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.*

@Component
class KakaApiClient(
    private val kakaApiWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun createKvalitetsvurdering(kvalitetsvurderingVersion: Int): KakaOutput {
        logger.debug("Creating kvalitetsvurdering i kaka")
        return kakaApiWebClient.post()
            .uri { it.path("/kabal/kvalitetsvurderinger/v$kvalitetsvurderingVersion").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKakaApiScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::createKvalitetsvurdering.name, secureLogger)
            }
            .bodyToMono<KakaOutput>()
            .block() ?: throw RuntimeException("Kvalitetsvurdering could not be created")
    }

    fun finalizeBehandling(saksdataInput: SaksdataInput, kvalitetsvurderingVersion: Int) {
        kakaApiWebClient.post()
            .uri { it.path("/kabal/saksdata/v$kvalitetsvurderingVersion").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKakaApiScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(saksdataInput)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::finalizeBehandling.name, secureLogger)
            }
            .bodyToMono<Void>()
            .block()
    }

    fun deleteKvalitetsvurderingV2(kvalitetsvurderingId: UUID) {
        kakaApiWebClient.delete()
            .uri { it.path("/kabal/kvalitetsvurderinger/v2/$kvalitetsvurderingId").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKakaApiScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::deleteKvalitetsvurderingV2.name, secureLogger)
            }
            .bodyToMono<Void>()
            .block()
    }

    fun getValidationErrors(
        kvalitetsvurderingId: UUID,
        ytelseId: String,
        typeId: String,
        kvalitetsvurderingVersion: Int
    ): ValidationErrors {
        logger.debug("Getting validation errors from kaka-api")
        return kakaApiWebClient.get()
            .uri {
                it.path("/kabal/kvalitetsvurderinger/v$kvalitetsvurderingVersion/{kvalitetsvurderingId}/validationerrors")
                    .queryParam("ytelseId", ytelseId)
                    .queryParam("typeId", typeId)
                    .build(kvalitetsvurderingId)
            }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKakaApiScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getValidationErrors.name, secureLogger)
            }
            .bodyToMono<ValidationErrors>()
            .block() ?: throw RuntimeException("Validation errors could not be retrieved")
    }
}