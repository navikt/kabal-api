package no.nav.klage.oppgave.clients.regoppslag


import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Component
class RegoppslagClient(
    private val regoppslagWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    fun getMottakerOgAdresse(input: Request, token: String): HentMottakerOgAdresseResponse? {
        logger.debug("Calling getMottakerOgAdresse")
        return regoppslagWebClient.post()
            .uri { it.path("/rest/hentMottakerOgAdresse").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer $token"
            )
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                response.bodyToMono(String::class.java).flatMap {
                    val errorString = "Got ${response.statusCode()} when requesting ${::getMottakerOgAdresse.name}"
                    //Debug is enough because this is not always an error
                    logger.debug("$errorString. See team-logs for more details.")
                    teamLogger.warn("$errorString - response body: '$it'")
                    Mono.error(RuntimeException(errorString))
                }
            }
            .bodyToMono<HentMottakerOgAdresseResponse>()
            .onErrorResume { Mono.empty() }
            .block()
    }

    data class Request(
        val identifikator: String,
        val type: RegoppslagType,
    ) {
        enum class RegoppslagType {
            ORGANISASJON,
            PERSON
        }
    }

    data class HentMottakerOgAdresseResponse(
        val identifikator: String,
        val navn: String,
        val adresse: Treg002Adresse,
    ) {
        data class Treg002Adresse(
            val adresselinje1: String?,
            val adresselinje2: String?,
            val adresselinje3: String?,
            val postnummer: String?,
            val poststed: String?,
            val landkode: String?,
        )
    }
}