package no.nav.klage.oppgave.clients.dokdistkanal

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

@Component
class DokDistKanalClient(
    private val dokDistKanalWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        private val noDistributionResponse =
            BestemDistribusjonskanalResponse(
                distribusjonskanal = BestemDistribusjonskanalResponse.DistribusjonKanalCode.INGEN_DISTRIBUSJON,
                regel = "",
                regelBegrunnelse = "",
            )
    }

    fun getDistribusjonskanal(input: Request): BestemDistribusjonskanalResponse {
        logger.debug("Calling getDistribusjonskanal")
        return dokDistKanalWebClient
            .post()
            .uri { it.path("/rest/bestemDistribusjonskanal").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getOnBehalfOfTokenWithDokDistKanalScope()}",
            ).bodyValue(input)
            .exchangeToMono(::handleResponse)
            .block() ?: throw RuntimeException("Null response from getDistribusjonskanal")
    }

    fun getDistribusjonskanalWithAppAccess(input: Request): BestemDistribusjonskanalResponse {
        logger.debug("Calling getDistribusjonskanalWithAppAccess")
        return dokDistKanalWebClient
            .post()
            .uri { it.path("/rest/bestemDistribusjonskanal").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithDokDistKanalScope()}",
            ).bodyValue(input)
            .exchangeToMono(::handleResponse)
            .block() ?: throw RuntimeException("Null response from getDistribusjonskanal")
    }

    private fun handleResponse(response: ClientResponse): Mono<BestemDistribusjonskanalResponse> =
        when {
            response.statusCode() == HttpStatus.GONE -> {
                Mono.just(noDistributionResponse)
            }

            response.statusCode().isError -> {
                logErrorResponse(
                    response = response,
                    functionName = ::getDistribusjonskanal.name,
                    classLogger = logger,
                ).flatMap { Mono.error(it) }
            }

            else -> {
                response.bodyToMono<BestemDistribusjonskanalResponse>()
            }
        }

    data class Request(
        val mottakerId: String,
        val brukerId: String,
        val tema: String,
        val erArkivert: Boolean = true,
    )

    data class BestemDistribusjonskanalResponse(
        val distribusjonskanal: DistribusjonKanalCode,
        val regel: String,
        val regelBegrunnelse: String,
    ) {
        enum class DistribusjonKanalCode(
            val utsendingkanalCode: UtsendingkanalCode,
        ) {
            PRINT(UtsendingkanalCode.S),
            SDP(UtsendingkanalCode.SDP),
            DITT_NAV(UtsendingkanalCode.NAV_NO),
            LOKAL_PRINT(UtsendingkanalCode.L),
            INGEN_DISTRIBUSJON(UtsendingkanalCode.INGEN_DISTRIBUSJON),
            TRYGDERETTEN(UtsendingkanalCode.TRYGDERETTEN),
            DPVT(UtsendingkanalCode.DPVT),
        }

        enum class UtsendingkanalCode {
            S,
            SDP,
            NAV_NO,
            L,
            INGEN_DISTRIBUSJON,
            TRYGDERETTEN,
            DPVT,
        }
    }
}
