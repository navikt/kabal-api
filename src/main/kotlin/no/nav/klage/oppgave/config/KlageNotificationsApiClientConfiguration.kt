package no.nav.klage.oppgave.config

import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class KlageNotificationsApiClientConfiguration(
    private val webClientBuilder: WebClient.Builder
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Value($$"${KLAGE_NOTIFICATIONS_API_BASE_URL}")
    private lateinit var klageNotificationsApiUrl: String

    @Bean
    fun klageNotificationsApiWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(klageNotificationsApiUrl)
            .build()
    }
}