package no.nav.klage.oppgave.config

import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class KabalDocumentClientConfiguration(
    private val webClientBuilder: WebClient.Builder
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Value("\${KABAL_DOCUMENT_BASE_URL}")
    private lateinit var kabalDocumentURL: String

    @Bean
    fun kabalDocumentWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(kabalDocumentURL)
            .build()
    }
}