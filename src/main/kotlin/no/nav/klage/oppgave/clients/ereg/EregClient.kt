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
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

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

    fun hentNoekkelInformasjonOmOrganisasjon(orgnummer: String): NoekkelInfoOmOrganisasjon {
        return eregWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/organisasjon/{orgnummer}/noekkelinfo")
                    .queryParam("inkluderHierarki", false)
                    .build(orgnummer)
            }
            .accept(MediaType.APPLICATION_JSON)
            .header("Nav-Consumer-Id", applicationName)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                when (response.statusCode().value()) {
                    404 -> {
                        Mono.error(EREGOrganizationNotFoundException("Fant ingen organisasjon med orgnummer $orgnummer"))
                    }

                    else -> {
                        logErrorResponse(response, ::hentNoekkelInformasjonOmOrganisasjon.name, secureLogger)
                        Mono.error(RuntimeException("Ukjent feil ved henting av nøkkelinfo om organisasjon $orgnummer"))
                    }
                }
            }
            .bodyToMono<NoekkelInfoOmOrganisasjon>()
            .block()
            ?: throw RuntimeException("hentNoekkelInformasjonOmOrganisasjon for $orgnummer returned null.")

    }

    fun isOrganisasjonActive(orgnummer: String) = hentNoekkelInformasjonOmOrganisasjon(orgnummer).isActive()
}