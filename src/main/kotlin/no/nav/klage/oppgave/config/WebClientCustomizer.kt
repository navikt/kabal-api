package no.nav.klage.oppgave.config

import io.opentelemetry.api.trace.Span
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient

/**
 * Common configuration for all web clients.
 */
@Component
class WebClientCustomizer : WebClientCustomizer {

    override fun customize(webClientBuilder: WebClient.Builder) {
        val headersWithTraceId = listOf(
            "Nav-Call-Id",
            "Nav-Callid",
            "X-Correlation-ID",
        )

        webClientBuilder
            .clientConnector(ReactorClientHttpConnector(HttpClient.newConnection()))
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .filter(
                ExchangeFilterFunction.ofRequestProcessor { request ->
                    val traceId = Span.current().spanContext.traceId
                    Mono.just(
                        ClientRequest.from(request)
                            .headers { headers ->
                                headersWithTraceId.forEach { headerName ->
                                    headers[headerName] = traceId
                                }
                            }
                            .build()
                    )
                }
            )
    }
}