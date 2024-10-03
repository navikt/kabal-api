package no.nav.klage.oppgave.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SafClientConfiguration(private val webClientBuilder: WebClient.Builder) {

    @Value("\${SAF_BASE_URL}")
    private lateinit var safUrl: String

    @Bean
    fun safWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(safUrl)
            .build()
    }
}
