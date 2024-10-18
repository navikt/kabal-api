package no.nav.klage.oppgave.clients.oppgaveapi

import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
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

    fun fetchOppgaveForAktoerIdAndTema(
        aktoerId: String,
        tema: Tema?
    ): List<OppgaveApiRecord> {
        val oppgaveResponse =
            logTimingAndWebClientResponseException(OppgaveApiClient::fetchOppgaveForAktoerIdAndTema.name) {
                oppgaveApiWebClient.get()
                    .uri { uriBuilder ->
                        uriBuilder.pathSegment("oppgaver")
                        uriBuilder.queryParam("aktoerId", aktoerId)
                        tema?.let { uriBuilder.queryParam("tema", it.navn) }
                        uriBuilder.queryParam("limit", 1000)
                        uriBuilder.queryParam("offset", 0)
                        uriBuilder.build()
                    }
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithOppgaveApiScope()}"
                    )
                    .header("Nav-Consumer-Id", applicationName)
                    .retrieve()
                    .bodyToMono<OppgaveListResponse>()
                    .block() ?: throw OppgaveClientException("Oppgaver could not be fetched")
            }

        return oppgaveResponse.oppgaver
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

    @Cacheable(CacheWithJCacheConfiguration.GOSYSOPPGAVE_GJELDER_CACHE)
    fun getGjelderKodeverkForTema(tema: Tema): List<Gjelder> {
        val gjelderResponse =
            logTimingAndWebClientResponseException(OppgaveApiClient::getGjelderKodeverkForTema.name) {
                oppgaveApiWebClient.get()
                    .uri { uriBuilder ->
                        uriBuilder.pathSegment("kodeverk", "gjelder", "{tema}")
                        uriBuilder.build(tema.navn)
                    }
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithOppgaveApiScope()}"
                    )
                    .header("Nav-Consumer-Id", applicationName)
                    .retrieve()
                    .bodyToMono<List<Gjelder>>()
                    .block() ?: throw OppgaveClientException("Could not fetch gjelder kodeverk for tema ${tema.navn}")
            }

        return gjelderResponse
    }

    @Cacheable(CacheWithJCacheConfiguration.GOSYSOPPGAVE_OPPGAVETYPE_CACHE)
    fun getOppgavetypeKodeverkForTema(tema: Tema): List<OppgavetypeResponse> {
        val oppgavetypeResponse =
            logTimingAndWebClientResponseException(OppgaveApiClient::getGjelderKodeverkForTema.name) {
                oppgaveApiWebClient.get()
                    .uri { uriBuilder ->
                        uriBuilder.pathSegment("kodeverk", "oppgavetype", "{tema}")
                        uriBuilder.build(tema.navn)
                    }
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithOppgaveApiScope()}"
                    )
                    .header("Nav-Consumer-Id", applicationName)
                    .retrieve()
                    .bodyToMono<List<OppgavetypeResponse>>()
                    .block() ?: throw OppgaveClientException("Could not fetch oppgavetype kodeverk for tema ${tema.navn}")
            }

        return oppgavetypeResponse
    }
}