package no.nav.klage.oppgave.config

import io.opentelemetry.api.trace.Span
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Configuration
class GosysOppgaveClientConfiguration(private val webClientBuilder: WebClient.Builder) {
    @Value("\${GOSYS_OPPGAVE_BASE_URL}")
    private lateinit var gosysOppgaveBaseURL: String

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Bean
    fun gosysOppgaveWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(gosysOppgaveBaseURL)
            .filter(
                ExchangeFilterFunction.ofRequestProcessor { request ->
                    val traceId = Span.current().spanContext.traceId
                    Mono.just(
                        ClientRequest.from(request)
                            .headers { headers ->
                                headers["Nav-Call-ID"] = traceId
                                headers["X-Correlation-ID"] = traceId
                            }
                            .build()
                    )
                }
            )
            .build()
    }
}
