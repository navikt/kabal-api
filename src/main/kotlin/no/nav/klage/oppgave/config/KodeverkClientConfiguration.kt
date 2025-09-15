package no.nav.klage.oppgave.config


import io.opentelemetry.api.trace.Span
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient


@Configuration
class KodeverkClientConfiguration(private val webClientBuilder: WebClient.Builder) {

    @Value("\${KODEVERK_API_URL}")
    private lateinit var url: String

    @Value("\${spring.application.name}")
    private lateinit var applicationName: String

    @Bean
    fun kodeverkWebClient(): WebClient =
        webClientBuilder
            .baseUrl(url)
            .defaultHeader("Nav-Consumer-Id", applicationName)
            .defaultHeader("Nav-Call-ID", Span.current().spanContext.traceId)
            .build()
}