package no.nav.klage.oppgave.clients.regoppslag


import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class RegoppslagClient(
    private val regoppslagWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun getMottakerOgAdresse(input: Request): HentMottakerOgAdresseResponse {
        logger.debug("Calling getMottakerOgAdresse")
        return regoppslagWebClient.post()
            .uri { it.path("/rest/hentMottakerOgAdresse").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getOnBehalfOfTokenWithRegoppslagScope()}"
            )
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getMottakerOgAdresse.name, secureLogger)
            }
            .bodyToMono<HentMottakerOgAdresseResponse>()
            .block() ?: throw RuntimeException("Null response from regoppslag")
    }
    data class Request(
        val identifikator: String,
        val type: RegoppslagType,
    ) {
        enum class RegoppslagType{
            ORGANISASJON,
            PERSON
        }
    }

    data class HentMottakerOgAdresseResponse (
        val identifikator: String,
        val navn: String,
        val adresse: Treg002Adresse,
    ) {
        data class Treg002Adresse(
            val adresselinje1: String,
            val adresselinje2: String?,
            val adresselinje3: String?,
            val postnummer: String,
            val poststed: String,
            val landkode: String,
        )
    }
}