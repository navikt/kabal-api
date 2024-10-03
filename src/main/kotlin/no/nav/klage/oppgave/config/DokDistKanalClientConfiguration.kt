package no.nav.klage.oppgave.config


import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class DokDistKanalClientConfiguration(private val webClientBuilder: WebClient.Builder) {

    @Value("\${DOK_DIST_KANAL_URL}")
    private lateinit var url: String

    @Value("\${spring.application.name}")
    private lateinit var applicationName: String

    @Bean
    fun dokDistKanalWebClient(): WebClient =
        webClientBuilder
            .baseUrl(url)
            .defaultHeader("Nav-Consumer-Id", applicationName)
            .build()
}