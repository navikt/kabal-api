package no.nav.klage.oppgave.clients.klanke

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
class KlankeClient(
    private val klankeWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getSakWithAppAccess(sakId: String, input: GetSakAppAccessInput): SakFromKlanke {
        return klankeWebClient.post()
            .uri { it.path("/rest/saker/{sakId}").build(sakId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKlankeScope()}"
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
        klankeWebClient.post()
            .uri { it.path("/rest/saker/{sakId}/handledinkabal").build(sakId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getOnBehalfOfTokenWithKlankeScope()}"
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
        klankeWebClient.post()
            .uri { it.path("/rest/saker/{sakId}/finished").build(sakId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKlankeScope()}"
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
        klankeWebClient.post()
            .uri { it.path("/rest/saker/{sakId}/assignedinkabal").build(sakId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKlankeScope()}"
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
        klankeWebClient.post()
            .uri { it.path("/rest/saker/{sakId}/feilregistrertinkabal").build(sakId) }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKlankeScope()}"
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
