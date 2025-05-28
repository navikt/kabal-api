package no.nav.klage.oppgave.clients.arbeidoginntekt

import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono


@Component
class ArbeidOgInntektClient(
    private val arbeidOgInntektWebClient: WebClient,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getAInntektUrl(
        personIdent: String
    ): String {
        return arbeidOgInntektWebClient.get()
            .uri("api/v2/redirect/sok/a-inntekt")
            .header(
                "Nav-Personident",
                personIdent
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getAInntektUrl.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<String>()
            .block() ?: throw RuntimeException("No AInntekt url returned")
    }

    fun getAARegisterUrl(
        personIdent: String
    ): String {
        return arbeidOgInntektWebClient.get()
            .uri("api/v2/redirect/sok/arbeidstaker")
            .header(
                "Nav-Personident",
                personIdent
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(
                    response = response,
                    functionName = ::getAARegisterUrl.name,
                    classLogger = logger,
                )
            }
            .bodyToMono<String>()
            .block() ?: throw RuntimeException("No AAreg url returned")
    }
}


