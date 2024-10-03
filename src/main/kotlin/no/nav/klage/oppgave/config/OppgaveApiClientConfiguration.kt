package no.nav.klage.oppgave.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class OppgaveApiClientConfiguration(private val webClientBuilder: WebClient.Builder) {
    @Value("\${OPPGAVE_API_BASE_URL}")
    private lateinit var oppgaveBaseURL: String

    @Bean
    fun oppgaveApiWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(oppgaveBaseURL)
            .build()
    }

}
