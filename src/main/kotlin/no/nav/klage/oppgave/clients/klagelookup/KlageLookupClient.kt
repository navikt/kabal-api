package no.nav.klage.oppgave.clients.klagelookup

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.service.TilgangService
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono


@Component
class KlageLookupClient(
    private val klageLookupWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Retryable
    fun getAccess(
        /** fnr, dnr or aktorId */
        brukerId: String,
        navIdent: String?,
        sakId: String?,
        ytelse: Ytelse?,
        fagsystem: Fagsystem?,
    ): TilgangService.Access {
        return runWithTimingAndLogging {
            val token = if (navIdent != null) {
                "Bearer ${tokenUtil.getAppAccessTokenWithKlageLookupScope()}"
            } else {
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKlageLookupScope()}"
            }

            val accessRequest = AccessRequest(
                brukerId = brukerId,
                navIdent = navIdent,
                sak = if (sakId != null && ytelse != null && fagsystem != null) AccessRequest.Sak(sakId = sakId, ytelse = ytelse, fagsystem = fagsystem) else null,
            )

            klageLookupWebClient.post()
                .uri("/access-to-person")
                .bodyValue(accessRequest)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    token,
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    logErrorResponse(
                        response = response,
                        functionName = ::getAccess.name,
                        classLogger = logger,
                    )
                }
                .bodyToMono<TilgangService.Access>()
                .block() ?: throw RuntimeException("Could not get access")
        }
    }

    fun <T> runWithTimingAndLogging(block: () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return block.invoke()
        } finally {
            val end = System.currentTimeMillis()
            logger.debug("Time it took to call klage-lookup: ${end - start} millis")
        }
    }
}