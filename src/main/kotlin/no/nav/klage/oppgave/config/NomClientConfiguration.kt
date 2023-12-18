package no.nav.klage.oppgave.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
class NomClientConfiguration(private val webClientBuilder: WebClient.Builder) {

    @Value("\${NOM_BASE_URL}")
    private lateinit var nomUrl: String

    @Bean
    fun nomWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(nomUrl)
            .clientConnector(ReactorClientHttpConnector(HttpClient.newConnection()))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}