package no.nav.klage.oppgave.clients.oppgaveapi

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class OppgaveApiClient(
    private val oppgaveApiWebClient: WebClient,
    private val tokenUtil: TokenUtil,
    @Value("\${spring.application.name}") private val applicationName: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securelogger = getSecureLogger()
    }

    fun getOppgave(oppgaveId: Long, systemContext: Boolean): OppgaveApiRecord {
        return logTimingAndWebClientResponseException(OppgaveApiClient::getOppgave.name) {
            oppgaveApiWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder.pathSegment("oppgaver", "{id}").build(oppgaveId)
                }
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${if (systemContext) tokenUtil.getAppAccessTokenWithOppgaveApiScope() else tokenUtil.getSaksbehandlerAccessTokenWithOppgaveApiScope()}"
                )
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<OppgaveApiRecord>()
                .block() ?: throw OppgaveClientException("Oppgave could not be fetched")
        }
    }

    fun updateOppgave(
        oppgaveId: Long,
        updateOppgaveInput: UpdateOppgaveRequest,
        systemContext: Boolean
    ): OppgaveApiRecord {
        return logTimingAndWebClientResponseException(OppgaveApiClient::updateOppgave.name) {
            oppgaveApiWebClient.patch()
                .uri { uriBuilder ->
                    uriBuilder.pathSegment("oppgaver", "{id}").build(oppgaveId)
                }
                .bodyValue(updateOppgaveInput)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${if (systemContext) tokenUtil.getAppAccessTokenWithOppgaveApiScope() else tokenUtil.getSaksbehandlerAccessTokenWithOppgaveApiScope()}"
                )
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<OppgaveApiRecord>()
                .block() ?: throw OppgaveClientException("Oppgave could not be updated")
        }
    }

    fun getMapperForEnhet(
        enhetsnr: String
    ): OppgaveMapperResponse {
        return logTimingAndWebClientResponseException(OppgaveApiClient::getMapperForEnhet.name) {
            oppgaveApiWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/mapper")
                        .queryParam("enhetsnr", enhetsnr)
                        .build()
                }
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithOppgaveApiScope()}"
                )
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<OppgaveMapperResponse>()
                .block() ?: throw OppgaveClientException("Could not get mapper for enhet")
        }
    }

    private fun <T> logTimingAndWebClientResponseException(methodName: String, function: () -> T): T {
        val start: Long = System.currentTimeMillis()
        try {
            return function.invoke()
        } catch (ex: WebClientResponseException) {
            logger.warn("Caught WebClientResponseException, see securelogs for details")
            securelogger.error(
                "Got a {} error calling Oppgave {} {} with message {}",
                ex.statusCode,
                ex.request?.method ?: "-",
                ex.request?.uri ?: "-",
                ex.responseBodyAsString
            )
            throw OppgaveClientException("Caught WebClientResponseException", ex)
        } catch (rtex: RuntimeException) {
            logger.warn("Caught RuntimeException", rtex)
            throw OppgaveClientException("Caught runtimeexception", rtex)
        } finally {
            val end: Long = System.currentTimeMillis()
            logger.info("Method {} took {} millis", methodName, (end - start))
        }
    }
}