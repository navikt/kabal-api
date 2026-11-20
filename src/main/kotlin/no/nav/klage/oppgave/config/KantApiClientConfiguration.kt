package no.nav.klage.oppgave.config

import io.netty.channel.ChannelOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class KantApiClientConfiguration(
    private val webClientBuilder: WebClient.Builder,
) {
    @Value($$"${KANT_API_BASE_URL}")
    private lateinit var kantApiBaseUrl: String

    @Bean
    fun kantApiWebClient(): WebClient {
        val httpClient =
            HttpClient
                .create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000)
                .responseTimeout(Duration.ofSeconds(5))

        return webClientBuilder
            .clone()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .baseUrl(kantApiBaseUrl)
            .build()
    }
}
