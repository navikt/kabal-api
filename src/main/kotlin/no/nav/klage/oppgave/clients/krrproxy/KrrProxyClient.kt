package no.nav.klage.oppgave.clients.krrproxy

import brave.Tracer
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Component
class KrrProxyClient(
    private val krrProxyWebClient: WebClient,
    private val tokenUtil: TokenUtil,
    private val tracer: Tracer
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Cacheable(CacheWithJCacheConfiguration.KRR_INFO_CACHE)
    fun getDigitalKontaktinformasjonForFnrOnBehalfOf(fnr: String): DigitalKontaktinformasjon? {
        return getDigitalKontaktinformasjon(fnr = fnr, token = tokenUtil.getOnBehalfOfTokenWithKrrProxyScope())
    }

    @Cacheable(CacheWithJCacheConfiguration.KRR_INFO_CACHE)
    fun getDigitalKontaktinformasjonForFnrAppAccess(fnr: String): DigitalKontaktinformasjon? {
        return getDigitalKontaktinformasjon(fnr = fnr, token = tokenUtil.getAppAccessTokenWithKrrProxyScope())
    }

    private fun getDigitalKontaktinformasjon(fnr: String, token: String): DigitalKontaktinformasjon? {
        logger.debug("Getting info from KRR")
        return krrProxyWebClient.get()
            .uri("/rest/v1/person")
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer $token"
            )
            .header(
                "Nav-Personident",
                fnr
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getDigitalKontaktinformasjonForFnrOnBehalfOf.name, secureLogger)
            }
            .bodyToMono<DigitalKontaktinformasjon>()
            .onErrorResume { Mono.empty() }
            .block()
    }

}