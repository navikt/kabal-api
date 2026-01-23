package no.nav.klage.oppgave.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class KlageLookupClientConfiguration(private val webClientBuilder: WebClient.Builder) {

    @Value($$"${KLAGE_LOOKUP_BASE_URL}")
    private lateinit var klageLookupUrl: String

    @Bean
    fun klageLookupWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(klageLookupUrl)
            .build()
    }
}