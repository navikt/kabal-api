package no.nav.klage.oppgave.clients.kaka

import no.nav.klage.oppgave.clients.kaka.model.response.KvalitetsvurderingOutput
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class KakaApiClient(
    private val kakaApiWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createKvalitetsvurdering(): KvalitetsvurderingOutput {
        return kakaApiWebClient.post()
            .uri { it.path("/kvalitetsvurdering").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKakaApiScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve()
            .bodyToMono<KvalitetsvurderingOutput>()
            .block() ?: throw RuntimeException("Kvalitetsvurdering could not be created")
    }
}