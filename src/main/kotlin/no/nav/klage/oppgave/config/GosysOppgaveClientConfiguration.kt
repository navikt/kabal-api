package no.nav.klage.oppgave.config

import io.opentelemetry.api.trace.Span
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class GosysOppgaveClientConfiguration(private val webClientBuilder: WebClient.Builder) {
    @Value("\${GOSYS_OPPGAVE_BASE_URL}")
    private lateinit var gosysOppgaveBaseURL: String

    @Bean
    fun gosysOppgaveWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(gosysOppgaveBaseURL)
            .defaultHeader("Nav-Call-ID", Span.current().spanContext.traceId)
            .defaultHeader("X-Correlation-ID", Span.current().spanContext.traceId)
            .build()
    }
}
