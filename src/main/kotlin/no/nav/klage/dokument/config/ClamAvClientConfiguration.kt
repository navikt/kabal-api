package no.nav.klage.dokument.config

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient


@Configuration
class ClamAvClientConfiguration(
    @Qualifier("webClientBuilder") private val webClientBuilder: WebClient.Builder,
    @Qualifier("clamAvLargeFileWebClientBuilder") private val clamAvLargeFileWebClientBuilder: WebClient.Builder,
) {

    @Value($$"${CLAM_AV_URL}")
    private lateinit var url: String

    @Bean
    fun clamAvWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(url)
            .build()
    }

    @Bean
    fun clamAvLargeFileWebClient(): WebClient {
        return clamAvLargeFileWebClientBuilder
            .baseUrl(url)
            .build()
    }
}
