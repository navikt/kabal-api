package no.nav.klage.oppgave.clients.kabalinnstillinger

import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.*
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class KabalInnstillingerClient(
    private val kabalInnstillingerWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Retryable
    fun getTildelteYtelserForEnhet(enhet: String): TildelteYtelserResponse {
        logger.debug("Getting tildelte ytelser for enhet $enhet in kabal-innstillinger")
        return kabalInnstillingerWebClient.get()
            .uri { it.path("/enhet/$enhet/tildelteytelser").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getUserAccessTokenWithKabalInnstillingerScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getTildelteYtelserForEnhet.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<TildelteYtelserResponse>()
            .block() ?: throw RuntimeException("Could not get tildelte ytelser for enhet $enhet")
    }

    @Retryable
    fun getSaksbehandlersTildelteYtelser(navIdent: String): SaksbehandlerAccess {
        logger.debug("Getting tildelte ytelser for $navIdent in kabal-innstillinger")
        return kabalInnstillingerWebClient.get()
            .uri { it.path("/ansatte/$navIdent/tildelteytelser").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getUserAccessTokenWithKabalInnstillingerScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getSaksbehandlersTildelteYtelser.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<SaksbehandlerAccess>()
            .block() ?: throw RuntimeException("Could not get tildelte ytelser")
    }

    @Retryable
    fun getSaksbehandlersTildelteYtelserAppAccess(navIdent: String): SaksbehandlerAccess {
        logger.debug("Getting tildelte ytelser for $navIdent in kabal-innstillinger through app access")
        return kabalInnstillingerWebClient.get()
            .uri { it.path("/ansatte/$navIdent/tildelteytelser").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKabalInnstillingerScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getSaksbehandlersTildelteYtelser.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<SaksbehandlerAccess>()
            .block() ?: throw RuntimeException("Could not get tildelte ytelser")
    }

    @Retryable
    fun getHjemmelIdsForYtelse(ytelse: Ytelse): Set<String> {
        logger.debug("Getting all registered hjemler in kabal-innstillinger for ytelse $ytelse")
        return kabalInnstillingerWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/hjemler")
                    .queryParam("ytelseId", ytelse.id)
                    .build()
            }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKabalInnstillingerScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getHjemmelIdsForYtelse.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<Set<String>>()
            .block() ?: throw RuntimeException("Could not get hjemler for ytelse")
    }

    @Retryable
    fun searchMedunderskrivere(input: MedunderskrivereInput): Medunderskrivere {
        logger.debug("Searching medunderskrivere in kabal-innstillinger")
        return kabalInnstillingerWebClient.post()
            .uri { it.path("/search/medunderskrivere").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getUserAccessTokenWithKabalInnstillingerScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::searchMedunderskrivere.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<Medunderskrivere>()
            .block() ?: throw RuntimeException("Could not search medunderskrivere")
    }

    @Retryable
    fun searchSaksbehandlere(input: SaksbehandlerSearchInput): Saksbehandlere {
        logger.debug("Searching saksbehandlere in kabal-innstillinger")
        return kabalInnstillingerWebClient.post()
            .uri { it.path("/search/saksbehandlere").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getUserAccessTokenWithKabalInnstillingerScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::searchSaksbehandlere.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<Saksbehandlere>()
            .block() ?: throw RuntimeException("Could not search saksbehandlere")
    }

    @Retryable
    fun searchROL(input: SakInput): Saksbehandlere {
        logger.debug("Searching rol in kabal-innstillinger")
        return kabalInnstillingerWebClient.post()
            .uri { it.path("/search/rol").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getUserAccessTokenWithKabalInnstillingerScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::searchROL.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<Saksbehandlere>()
            .block() ?: throw RuntimeException("Could not search rol")
    }
}