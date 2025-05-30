package no.nav.klage.oppgave.clients.krrproxy

import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
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
        private val teamLogger = getTeamLogger()
    }

    @Cacheable(CacheWithJCacheConfiguration.KRR_INFO_CACHE)
    fun getDigitalKontaktinformasjonForFnrOnBehalfOf(fnr: String): DigitalKontaktinformasjon? {
        val krrProxyResponse =
            getDigitalKontaktinformasjon(fnr = fnr, token = tokenUtil.getOnBehalfOfTokenWithKrrProxyScope())
        if (krrProxyResponse?.feil?.get(fnr) != null) {
            logger.error("Error from KRR. Returning null. See team-logs for more details.")
            teamLogger.error("Error from KRR: ${krrProxyResponse.feil[fnr]}")
            return null
        } else return krrProxyResponse?.personer?.get(fnr)
    }

    @Cacheable(CacheWithJCacheConfiguration.KRR_INFO_CACHE)
    fun getDigitalKontaktinformasjonForFnrAppAccess(fnr: String): DigitalKontaktinformasjon? {
        val krrProxyResponse =
            getDigitalKontaktinformasjon(fnr = fnr, token = tokenUtil.getAppAccessTokenWithKrrProxyScope())
        if (krrProxyResponse?.feil?.get(fnr) != null) {
            logger.error("Error from KRR. Returning null. See team-logs for more details.")
            teamLogger.error("Error from KRR: ${krrProxyResponse.feil[fnr]}")
            return null
        } else return krrProxyResponse?.personer?.get(fnr)
    }

    private fun getDigitalKontaktinformasjon(fnr: String, token: String): KrrProxyResponse? {
        logger.debug("Getting info from KRR")
        return krrProxyWebClient.post()
            .uri("/rest/v1/personer")
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer $token"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(KrrProxyRequest(personidenter = setOf(fnr)))
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getDigitalKontaktinformasjon.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<KrrProxyResponse>()
            .onErrorResume { Mono.empty() }
            .block()
    }
}