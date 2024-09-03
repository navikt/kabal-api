package no.nav.klage.oppgave.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient.newConnection

@Configuration
class OppgaveApiClientConfiguration(private val webClientBuilder: WebClient.Builder) {
    @Value("\${OPPGAVE_API_BASE_URL}")
    private lateinit var oppgaveBaseURL: String

    @Bean("oppgaveApiWebClient")
    fun oppgaveApiWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(oppgaveBaseURL)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .clientConnector(ReactorClientHttpConnector(newConnection()))
            .build()
    }

}
