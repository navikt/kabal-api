package no.nav.klage.oppgave.clients.oppgaveapi

import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.clients.oppgaveapi.OppgaveMapperResponse.OppgaveMappe
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
class GosysOppgaveClient(
    private val oppgaveApiWebClient: WebClient,
    private val tokenUtil: TokenUtil,
    @Value("\${spring.application.name}") private val applicationName: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securelogger = getSecureLogger()
    }

    fun getGosysOppgave(gosysOppgaveId: Long, systemContext: Boolean): GosysOppgaveRecord {
        return logTimingAndWebClientResponseException(GosysOppgaveClient::getGosysOppgave.name) {
            oppgaveApiWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder.pathSegment("oppgaver", "{id}").build(gosysOppgaveId)
                }
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${if (systemContext) tokenUtil.getAppAccessTokenWithOppgaveApiScope() else tokenUtil.getSaksbehandlerAccessTokenWithOppgaveApiScope()}"
                )
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<GosysOppgaveRecord>()
                .block() ?: throw OppgaveClientException("Oppgave could not be fetched")
        }
    }

    fun updateGosysOppgave(
        gosysOppgaveId: Long,
        updateOppgaveInput: UpdateOppgaveRequest,
        systemContext: Boolean
    ): GosysOppgaveRecord {
        return logTimingAndWebClientResponseException(GosysOppgaveClient::updateGosysOppgave.name) {
            oppgaveApiWebClient.patch()
                .uri { uriBuilder ->
                    uriBuilder.pathSegment("oppgaver", "{id}").build(gosysOppgaveId)
                }
                .bodyValue(updateOppgaveInput)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${if (systemContext) tokenUtil.getAppAccessTokenWithOppgaveApiScope() else tokenUtil.getSaksbehandlerAccessTokenWithOppgaveApiScope()}"
                )
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<GosysOppgaveRecord>()
                .block() ?: throw OppgaveClientException("Oppgave could not be updated")
        }
    }

    @Cacheable(CacheWithJCacheConfiguration.GOSYSOPPGAVE_ENHETSMAPPER_CACHE)
    fun getMapperForEnhet(
        enhetsnr: String
    ): OppgaveMapperResponse {
        return logTimingAndWebClientResponseException(GosysOppgaveClient::getMapperForEnhet.name) {
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

    @Cacheable(CacheWithJCacheConfiguration.GOSYSOPPGAVE_ENHETSMAPPE_CACHE)
    fun getMappe(
        id: Long
    ): OppgaveMappe {
        return logTimingAndWebClientResponseException(GosysOppgaveClient::getMappe.name) {
            oppgaveApiWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/mapper/{id}")
                        .build(id)
                }
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithOppgaveApiScope()}"
                )
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<OppgaveMappe>()
                .block() ?: throw OppgaveClientException("Could not get mapper for enhet")
        }
    }

    fun fetchGosysOppgaveForAktoerIdAndTema(
        aktoerId: String,
        tema: Tema?
    ): List<GosysOppgaveRecord> {
        val oppgaveResponse =
            logTimingAndWebClientResponseException(GosysOppgaveClient::fetchGosysOppgaveForAktoerIdAndTema.name) {
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
            logTimingAndWebClientResponseException(GosysOppgaveClient::getGjelderKodeverkForTema.name) {
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
            logTimingAndWebClientResponseException(GosysOppgaveClient::getGjelderKodeverkForTema.name) {
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