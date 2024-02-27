package no.nav.klage.oppgave.clients.dokdistkanal

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
class DokDistKanalClient(
    private val dokDistKanalWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun getDistribusjonskanal(input: Request): BestemDistribusjonskanalResponse {
        logger.debug("Calling getDistribusjonskanal")
        return dokDistKanalWebClient.post()
            .uri { it.path("/rest/bestemDistribusjonskanal").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getOnBehalfOfTokenWithDokDistKanalScope()}"
            )
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getDistribusjonskanal.name, secureLogger)
            }
            .bodyToMono<BestemDistribusjonskanalResponse>()
            .block() ?: throw RuntimeException("Null response from getDistribusjonskanal")
    }

    fun getDistribusjonskanalWithAppAccess(input: Request): BestemDistribusjonskanalResponse {
        logger.debug("Calling getDistribusjonskanalWithAppAccess")
        return dokDistKanalWebClient.post()
            .uri { it.path("/rest/bestemDistribusjonskanal").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithDokDistKanalScope()}"
            )
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getDistribusjonskanal.name, secureLogger)
            }
            .bodyToMono<BestemDistribusjonskanalResponse>()
            .block() ?: throw RuntimeException("Null response from getDistribusjonskanal")
    }

    data class Request(
        val mottakerId: String,
        val brukerId: String,
        val tema: String,
    )

    data class BestemDistribusjonskanalResponse(
        val distribusjonskanal: DistribusjonKanalCode,
        val regel: String,
        val regelBegrunnelse: String
    ) {
        enum class DistribusjonKanalCode(val utsendingkanalCode: UtsendingkanalCode) {
            PRINT(UtsendingkanalCode.S),
            SDP(UtsendingkanalCode.SDP),
            DITT_NAV(UtsendingkanalCode.NAV_NO),
            LOKAL_PRINT(UtsendingkanalCode.L),
            INGEN_DISTRIBUSJON(UtsendingkanalCode.INGEN_DISTRIBUSJON),
            TRYGDERETTEN(UtsendingkanalCode.TRYGDERETTEN),
            DPVT(UtsendingkanalCode.DPVT);
        }

        enum class UtsendingkanalCode {
            S,
            SDP,
            NAV_NO,
            L,
            INGEN_DISTRIBUSJON,
            TRYGDERETTEN,
            DPVT
        }
    }
}