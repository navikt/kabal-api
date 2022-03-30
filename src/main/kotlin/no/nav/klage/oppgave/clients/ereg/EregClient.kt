package no.nav.klage.oppgave.clients.ereg


import brave.Tracer
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class EregClient(
    private val eregWebClient: WebClient,
    private val tracer: Tracer
) {

    @Value("\${spring.application.name}")
    lateinit var applicationName: String

    fun hentOrganisasjon(orgnummer: String): Organisasjon? {
        return kotlin.runCatching {
            eregWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/v1/organisasjon/{orgnummer}")
                        .queryParam("inkluderHierarki", false)
                        .build(orgnummer)
                }
                .accept(MediaType.APPLICATION_JSON)
                .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .bodyToMono<Organisasjon>()
                .block()
        }.fold(
            onSuccess = { it },
            onFailure = { error ->
                when (error) {
                    is WebClientResponseException.NotFound -> {
                        null
                    }
                    else -> throw error
                }
            }

        )
    }

    fun organisasjonExists(orgnummer: String): Boolean {
        return hentOrganisasjon(orgnummer) != null
    }
}