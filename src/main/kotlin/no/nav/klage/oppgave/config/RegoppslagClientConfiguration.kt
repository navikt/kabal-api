package no.nav.klage.oppgave.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class RegoppslagClientConfiguration(private val webClientBuilder: WebClient.Builder) {

    @Value("\${REGOPPSLAG_URL}")
    private lateinit var url: String

    @Value("\${spring.application.name}")
    private lateinit var applicationName: String

    @Bean
    fun regoppslagWebClient(): WebClient =
        webClientBuilder
            .baseUrl(url)
            .defaultHeader("Nav-Consumer-Id", applicationName)
            .build()
}