package no.nav.klage.oppgave.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SkjermedePersonerPipClientConfiguration(private val webClientBuilder: WebClient.Builder) {
    @Value("\${SKJERMEDE_PERSONER_PIP_BASE_URL}")
    private lateinit var skjermedePersonerPipBaseUrl: String

    @Bean
    fun skjermedePersonerPipWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(skjermedePersonerPipBaseUrl)
            .build()
    }
}