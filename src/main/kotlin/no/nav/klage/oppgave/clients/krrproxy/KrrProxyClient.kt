package no.nav.klage.oppgave.clients.krrproxy

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
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
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    //    @Cacheable(CacheWithJCacheConfiguration.KRR_INFO_CACHE)
    fun getDigitalKontaktinformasjonForFnrOnBehalfOf(fnr: String): DigitalKontaktinformasjon? {
        val krrProxyResponse =
            getDigitalKontaktinformasjonNew(fnr = fnr, token = tokenUtil.getOnBehalfOfTokenWithKrrProxyScope())
        logger.debug("KRR response: $krrProxyResponse")
        if (krrProxyResponse?.feil?.get(fnr) != null) {
            logger.error("Error from KRR: ${krrProxyResponse.feil[fnr]}")
            return null
        } else return krrProxyResponse?.personer?.get(fnr)
    }

    //    @Cacheable(CacheWithJCacheConfiguration.KRR_INFO_CACHE)
    fun getDigitalKontaktinformasjonForFnrAppAccess(fnr: String): DigitalKontaktinformasjon? {
        val krrProxyResponse =
            getDigitalKontaktinformasjonNew(fnr = fnr, token = tokenUtil.getAppAccessTokenWithKrrProxyScope())
        logger.debug("KRR response: $krrProxyResponse")
        if (krrProxyResponse?.feil?.get(fnr) != null) {
            logger.error("Error from KRR: ${krrProxyResponse.feil[fnr]}")
            return null
        } else return krrProxyResponse?.personer?.get(fnr)
    }

    private fun getDigitalKontaktinformasjonNew(fnr: String, token: String): KrrProxyResponse? {
        logger.debug("Getting info from KRR")
        return krrProxyWebClient.post()
            .uri("/rest/v1/person")
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer $token"
            )
            .bodyValue(KrrProxyRequest(personidenter = listOf(fnr)))
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getDigitalKontaktinformasjonNew.name, secureLogger)
            }
            .bodyToMono<KrrProxyResponse>()
            .onErrorResume { Mono.empty() }
            .block()
    }
}