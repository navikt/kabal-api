package no.nav.klage.oppgave.clients.gosysoppgave

import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.clients.gosysoppgave.OppgaveMapperResponse.OppgaveMappe
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import no.nav.klage.oppgave.exceptions.GosysOppgaveClientException
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class GosysOppgaveClient(
    private val gosysOppgaveWebClient: WebClient,
    private val tokenUtil: TokenUtil,
    @Value("\${spring.application.name}") private val applicationName: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    fun getGosysOppgave(gosysOppgaveId: Long, systemContext: Boolean): GosysOppgaveRecord {
        return logTimingAndWebClientResponseException(GosysOppgaveClient::getGosysOppgave.name) {
            gosysOppgaveWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder.pathSegment("oppgaver", "{id}").build(gosysOppgaveId)
                }
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${if (systemContext) tokenUtil.getAppAccessTokenWithGosysOppgaveScope() else tokenUtil.getSaksbehandlerAccessTokenWithGosysOppgaveScope()}"
                )
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<GosysOppgaveRecord>()
                .block() ?: throw RuntimeException("Oppgave could not be fetched")
        }
    }

    fun updateGosysOppgave(
        gosysOppgaveId: Long,
        updateOppgaveInput: UpdateOppgaveRequest,
        systemContext: Boolean
    ): GosysOppgaveRecord {
        return logTimingAndWebClientResponseException(GosysOppgaveClient::updateGosysOppgave.name) {
            gosysOppgaveWebClient.patch()
                .uri { uriBuilder ->
                    uriBuilder.pathSegment("oppgaver", "{id}").build(gosysOppgaveId)
                }
                .bodyValue(updateOppgaveInput)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${if (systemContext) tokenUtil.getAppAccessTokenWithGosysOppgaveScope() else tokenUtil.getSaksbehandlerAccessTokenWithGosysOppgaveScope()}"
                )
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<GosysOppgaveRecord>()
                .block() ?: throw RuntimeException("Oppgave could not be updated")
        }
    }

    @Cacheable(CacheWithJCacheConfiguration.GOSYSOPPGAVE_ENHETSMAPPER_CACHE)
    fun getMapperForEnhet(
        enhetsnr: String
    ): OppgaveMapperResponse {
        return logTimingAndWebClientResponseException(GosysOppgaveClient::getMapperForEnhet.name) {
            gosysOppgaveWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/mapper")
                        .queryParam("enhetsnr", enhetsnr)
                        .queryParam("offset", 0)
                        .queryParam("limit", 250)
                        .build()
                }
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithGosysOppgaveScope()}"
                )
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<OppgaveMapperResponse>()
                .block() ?: throw RuntimeException("Could not get mapper for enhet")
        }
    }

    @Cacheable(CacheWithJCacheConfiguration.GOSYSOPPGAVE_ENHETSMAPPE_CACHE)
    fun getMappe(
        id: Long,
        systemContext: Boolean
    ): OppgaveMappe {
        return logTimingAndWebClientResponseException(GosysOppgaveClient::getMappe.name) {
            gosysOppgaveWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/mapper/{id}")
                        .build(id)
                }
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${if (systemContext) tokenUtil.getAppAccessTokenWithGosysOppgaveScope() else tokenUtil.getSaksbehandlerAccessTokenWithGosysOppgaveScope()}"
                )
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<OppgaveMappe>()
                .block() ?: throw RuntimeException("Could not get mapper for enhet")
        }
    }

    fun fetchGosysOppgaveForAktoerIdAndTema(
        aktoerId: String,
        temaList: List<Tema>?
    ): List<GosysOppgaveRecord> {
        val oppgaveResponse =
            logTimingAndWebClientResponseException(GosysOppgaveClient::fetchGosysOppgaveForAktoerIdAndTema.name) {
                gosysOppgaveWebClient.get()
                    .uri { uriBuilder ->
                        uriBuilder.pathSegment("oppgaver")
                        uriBuilder.queryParam("aktoerId", aktoerId)
                        temaList?.let { uriBuilder.queryParam("tema", temaList.map { it.navn }) }
                        uriBuilder.queryParam("limit", 1000)
                        uriBuilder.queryParam("offset", 0)
                        uriBuilder.build()
                    }
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithGosysOppgaveScope()}"
                    )
                    .header("Nav-Consumer-Id", applicationName)
                    .retrieve()
                    .bodyToMono<OppgaveListResponse>()
                    .block() ?: throw RuntimeException("Oppgaver could not be fetched")
            }

        return oppgaveResponse.oppgaver
    }

    private fun <T> logTimingAndWebClientResponseException(methodName: String, function: () -> T): T {
        val start: Long = System.currentTimeMillis()
        try {
            return function.invoke()
        } catch (ex: WebClientResponseException) {
            logger.warn("Caught WebClientResponseException, see team-logs for details")
            teamLogger.error(
                "Got a {} error calling Oppgave {} {} with message {}",
                ex.statusCode,
                ex.request?.method ?: "-",
                ex.request?.uri ?: "-",
                ex.responseBodyAsString
            )
            throw GosysOppgaveClientException("Caught WebClientResponseException", ex)
        } catch (rtex: RuntimeException) {
            logger.warn("Caught RuntimeException", rtex)
            throw GosysOppgaveClientException("Caught runtimeexception", rtex)
        } finally {
            val end: Long = System.currentTimeMillis()
            logger.info("Method {} took {} millis", methodName, (end - start))
        }
    }

    @Cacheable(CacheWithJCacheConfiguration.GOSYSOPPGAVE_GJELDER_CACHE)
    fun getGjelderKodeverkForTema(tema: Tema, systemContext: Boolean): List<Gjelder> {
        val gjelderResponse =
            logTimingAndWebClientResponseException(GosysOppgaveClient::getGjelderKodeverkForTema.name) {
                gosysOppgaveWebClient.get()
                    .uri { uriBuilder ->
                        uriBuilder.pathSegment("kodeverk", "gjelder", "{tema}")
                        uriBuilder.build(tema.navn)
                    }
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer ${if (systemContext) tokenUtil.getAppAccessTokenWithGosysOppgaveScope() else tokenUtil.getSaksbehandlerAccessTokenWithGosysOppgaveScope()}"
                    )
                    .header("Nav-Consumer-Id", applicationName)
                    .retrieve()
                    .bodyToMono<List<Gjelder>>()
                    .block() ?: throw RuntimeException("Could not fetch gjelder kodeverk for tema ${tema.navn}")
            }

        return gjelderResponse
    }

    @Cacheable(CacheWithJCacheConfiguration.GOSYSOPPGAVE_OPPGAVETYPE_CACHE)
    fun getOppgavetypeKodeverkForTema(tema: Tema, systemContext: Boolean): List<OppgavetypeResponse> {
        val oppgavetypeResponse =
            logTimingAndWebClientResponseException(GosysOppgaveClient::getOppgavetypeKodeverkForTema.name) {
                gosysOppgaveWebClient.get()
                    .uri { uriBuilder ->
                        uriBuilder.pathSegment("kodeverk", "oppgavetype", "{tema}")
                        uriBuilder.build(tema.navn)
                    }
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer ${if (systemContext) tokenUtil.getAppAccessTokenWithGosysOppgaveScope() else tokenUtil.getSaksbehandlerAccessTokenWithGosysOppgaveScope()}"
                    )
                    .header("Nav-Consumer-Id", applicationName)
                    .retrieve()
                    .bodyToMono<List<OppgavetypeResponse>>()
                    .block() ?: throw RuntimeException("Could not fetch oppgavetype kodeverk for tema ${tema.navn}")
            }

        return oppgavetypeResponse
    }
}