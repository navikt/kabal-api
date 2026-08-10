package no.nav.klage.dokument.config

import io.netty.channel.ChannelOption
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class FileApiClientConfiguration(
    private val webClientBuilder: WebClient.Builder
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Value($$"${KABAL_FILE_API_BASE_URL}")
    private lateinit var fileServiceURL: String

    @Bean
    fun fileWebClient(): WebClient {
        //Confirming an upload triggers a synchronous virus scan of files up to 512 MB in kabal-file-api,
        //which can take a while, so use a generous response timeout for this client.
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
            .responseTimeout(Duration.ofSeconds(360))

        return webClientBuilder
            .clone()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .baseUrl(fileServiceURL)
            .build()
    }

    @Bean
    fun gcsDownloadWebClient(): WebClient {
        //Documents can be large, and they are streamed to disk, so allow plenty of time.
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
            .responseTimeout(Duration.ofSeconds(120))

        return webClientBuilder
            .clone()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}
