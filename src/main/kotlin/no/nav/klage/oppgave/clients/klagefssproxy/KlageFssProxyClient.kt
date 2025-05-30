package no.nav.klage.oppgave.clients.klagefssproxy

import no.nav.klage.oppgave.clients.klagefssproxy.domain.*
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class KlageFssProxyClient(
    private val klageFssProxyWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getSakWithAppAccess(sakId: String, input: GetSakAppAccessInput): SakFromKlanke {
        return klageFssProxyWebClient.post()
            .uri { it.path("/klanke/saker/{sakId}").build(sakId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKlageFSSProxyScope()}"
            )
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getSakWithAppAccess.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<SakFromKlanke>()
            .block()
            ?: throw RuntimeException("Empty result")
    }

    fun setToHandledInKabal(sakId: String, input: HandledInKabalInput) {
        klageFssProxyWebClient.post()
            .uri { it.path("/klanke/saker/{sakId}/handledinkabal").build(sakId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getOnBehalfOfTokenWithKlageFSSProxyScope()}"
            )
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::setToHandledInKabal.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<Unit>()
            .block()
    }

    fun setToFinishedWithAppAccess(sakId: String, input: SakFinishedInput) {
        klageFssProxyWebClient.post()
            .uri { it.path("/klanke/saker/{sakId}/finished").build(sakId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKlageFSSProxyScope()}"
            )
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::setToFinishedWithAppAccess.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<Unit>()
            .block()
    }

    fun setToAssigned(sakId: String, input: SakAssignedInput) {
        klageFssProxyWebClient.post()
            .uri { it.path("/klanke/saker/{sakId}/assignedinkabal").build(sakId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKlageFSSProxyScope()}"
            )
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::setToAssigned.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<Unit>()
            .block()
    }

    fun setToFeilregistrertInKabal(sakId: String, input: FeilregistrertInKabalInput) {
        klageFssProxyWebClient.post()
            .uri { it.path("/klanke/saker/{sakId}/feilregistrertinkabal").build(sakId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getOnBehalfOfTokenWithKlageFSSProxyScope()}"
            )
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::setToFeilregistrertInKabal.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<Unit>()
            .block()
    }

}