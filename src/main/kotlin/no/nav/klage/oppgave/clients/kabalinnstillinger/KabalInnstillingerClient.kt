package no.nav.klage.oppgave.clients.kabalinnstillinger

import no.nav.klage.oppgave.clients.kabalinnstillinger.model.*
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
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
        private val secureLogger = getSecureLogger()
    }

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
                logErrorResponse(response, ::getTildelteYtelserForEnhet.name, secureLogger)
            }
            .bodyToMono<TildelteYtelserResponse>()
            .block() ?: throw RuntimeException("Could not get tildelte ytelser for enhet $enhet")
    }

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
                logErrorResponse(response, ::getSaksbehandlersTildelteYtelser.name, secureLogger)
            }
            .bodyToMono<SaksbehandlerAccess>()
            .block() ?: throw RuntimeException("Could not get tildelte ytelser")
    }

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
                logErrorResponse(response, ::searchMedunderskrivere.name, secureLogger)
            }
            .bodyToMono<Medunderskrivere>()
            .block() ?: throw RuntimeException("Could not search medunderskrivere")
    }

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
                logErrorResponse(response, ::searchSaksbehandlere.name, secureLogger)
            }
            .bodyToMono<Saksbehandlere>()
            .block() ?: throw RuntimeException("Could not search saksbehandlere")
    }

    fun searchROL(input: ROLSearchInput): Saksbehandlere {
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
                logErrorResponse(response, ::searchROL.name, secureLogger)
            }
            .bodyToMono<Saksbehandlere>()
            .block() ?: throw RuntimeException("Could not search rol")
    }
}