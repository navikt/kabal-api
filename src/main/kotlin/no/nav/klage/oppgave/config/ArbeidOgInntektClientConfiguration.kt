package no.nav.klage.oppgave.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class ArbeidOgInntektClientConfiguration(private val webClientBuilder: WebClient.Builder) {

    @Value("\${ARBEID_OG_INNTEKT_URL}")
    private lateinit var arbeidOgInntektUrl: String

    @Bean
    fun arbeidOgInntektWebClient(): WebClient {
        return webClientBuilder
            .baseUrl(arbeidOgInntektUrl)
            .build()
    }
}
