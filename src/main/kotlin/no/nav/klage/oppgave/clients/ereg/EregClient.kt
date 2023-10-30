package no.nav.klage.oppgave.clients.ereg


import no.nav.klage.oppgave.exceptions.EREGOrganizationNotFoundException
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class EregClient(
    private val eregWebClient: WebClient,
) {

    @Value("\${spring.application.name}")
    lateinit var applicationName: String

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun hentOrganisasjon(orgnummer: String): Organisasjon {
        return kotlin.runCatching {
            eregWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/organisasjon/{orgnummer}")
                        .queryParam("inkluderHierarki", false)
                        .build(orgnummer)
                }
                .accept(MediaType.APPLICATION_JSON)
                .header("Nav-Consumer-Id", applicationName)
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    logErrorResponse(response, ::hentOrganisasjon.name, secureLogger)
                }
                .bodyToMono<Organisasjon>()
                .block() ?: throw EREGOrganizationNotFoundException("Search for organization $orgnummer in Ereg returned null.")
        }.fold(
            onSuccess = { it },
            onFailure = { error ->
                when (error) {
                    is WebClientResponseException.NotFound -> {
                        throw EREGOrganizationNotFoundException("Couldn't find organization $orgnummer in Ereg.")
                    }
                    else -> throw error
                }
            }

        )
    }

    fun isOrganisasjonActive(orgnummer: String) = hentOrganisasjon(orgnummer).isActive()
}