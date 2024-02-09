package no.nav.klage.oppgave.clients.kodeverk

import brave.Tracer
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import no.nav.klage.oppgave.exceptions.KodeverkNotFoundException
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class KodeverkClient(
    private val kodeverkWebClient: WebClient,
    private val tracer: Tracer,
    private val tokenUtil: TokenUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Cacheable(CacheWithJCacheConfiguration.POSTSTEDER_CACHE)
    fun getPoststeder(): KodeverkResponse {
        return kotlin.runCatching {
            kodeverkWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/Postnummer/koder/betydninger")
                        .queryParam("ekskluderUgyldige", true)
                        .queryParam("spraak", "NO")
                        .build()
                }
                .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKodeverkScope()}"
                )

                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    logErrorResponse(response, ::getPoststeder.name, secureLogger)
                }
                .bodyToMono<KodeverkResponse>()
                .block() ?: throw KodeverkNotFoundException("Search for Postnummer kodeverk returned null.")
        }.fold(
            onSuccess = { it },
            onFailure = { error ->
                when (error) {
                    is WebClientResponseException.NotFound -> {
                        throw KodeverkNotFoundException("Search for Postnummer kodeverk returned null.")
                    }

                    else -> throw error
                }
            }
        )
    }
}