package no.nav.klage.oppgave.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SkjermetClientConfiguration(private val webClientBuilder: WebClient.Builder) {

    @Value("\${SKJERMEDE_BASE_URL}")
    private lateinit var skjermedeUrl: String

    @Bean
    fun skjermedeWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(skjermedeUrl)
            .build()
    }
}