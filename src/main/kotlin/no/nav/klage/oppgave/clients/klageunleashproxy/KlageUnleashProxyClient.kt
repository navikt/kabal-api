package no.nav.klage.oppgave.clients.klageunleashproxy

import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class KlageUnleashProxyClient(
    @Value($$"${NAIS_POD_NAME}")
    private val naisPodName: String,
    @Value($$"${NAIS_APP_NAME}")
    private val naisAppName: String,
    private val klageUnleashProxyWebClient: WebClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Retryable
    fun isEnabled(feature: String, navIdent: String): Boolean {
        val requestBody = UnleashProxyRequest(
            navIdent = navIdent,
            appName = naisAppName,
            podName = naisPodName,
        )

        return klageUnleashProxyWebClient.post()
            .uri("/features/${feature}")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono<FeatureToggleResponse>()
            .block()?.enabled ?: false
    }
}