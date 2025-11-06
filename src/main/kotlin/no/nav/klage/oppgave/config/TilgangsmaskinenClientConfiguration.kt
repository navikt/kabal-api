package no.nav.klage.oppgave.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class TilgangsmaskinenClientConfiguration(private val webClientBuilder: WebClient.Builder) {

    @Value("\${TILGANGSMASKINEN_BASE_URL}")
    private lateinit var tilgangsmaskinenUrl: String

    @Bean
    fun tilgangsmaskinenWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(tilgangsmaskinenUrl)
            .build()
    }
}
