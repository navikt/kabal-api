package no.nav.klage.oppgave.clients

import io.mockk.every
import io.mockk.mockk
import no.nav.klage.oppgave.clients.dokdistkanal.DokDistKanalClient
import no.nav.klage.oppgave.clients.dokdistkanal.DokDistKanalClient.BestemDistribusjonskanalResponse
import no.nav.klage.oppgave.clients.dokdistkanal.DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode
import no.nav.klage.oppgave.util.TokenUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

class DokDistKanalClientTest {
    @ParameterizedTest
    @EnumSource(AccessVariant::class)
    fun `410 with empty body returns no distribution`(accessVariant: AccessVariant) {
        val result = client(HttpStatus.GONE).get(accessVariant)

        assertEquals(noDistributionResponse, result)
    }

    @ParameterizedTest
    @EnumSource(AccessVariant::class)
    fun `410 with arbitrary body returns no distribution`(accessVariant: AccessVariant) {
        val result = client(status = HttpStatus.GONE, body = "not-json").get(accessVariant)

        assertEquals(noDistributionResponse, result)
    }

    @ParameterizedTest
    @EnumSource(AccessVariant::class)
    fun `200 response fields are preserved`(accessVariant: AccessVariant) {
        val result = client(status = HttpStatus.OK, body = successResponse).get(accessVariant)

        assertEquals(
            BestemDistribusjonskanalResponse(
                distribusjonskanal = DistribusjonKanalCode.DITT_NAV,
                regel = "synthetic-rule",
                regelBegrunnelse = "synthetic-reason",
            ),
            result,
        )
    }

    @ParameterizedTest
    @EnumSource(AccessVariant::class)
    fun `non-410 errors are propagated`(accessVariant: AccessVariant) {
        listOf(HttpStatus.BAD_REQUEST, HttpStatus.NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR).forEach { status ->
            val exception =
                assertThrows(WebClientResponseException::class.java) {
                    client(status = status, body = "synthetic error").get(accessVariant)
                }

            assertEquals(status, exception.statusCode)
        }
    }

    @ParameterizedTest
    @EnumSource(AccessVariant::class)
    fun `empty 200 response retains null response failure`(accessVariant: AccessVariant) {
        val exception =
            assertThrows(RuntimeException::class.java) {
                client(HttpStatus.OK).get(accessVariant)
            }

        assertEquals("Null response from getDistribusjonskanal", exception.message)
    }

    private fun client(
        status: HttpStatus,
        body: String? = null,
    ): DokDistKanalClient {
        val responseBuilder = ClientResponse.create(status)
        if (body != null) {
            responseBuilder.header("Content-Type", if (status == HttpStatus.OK) "application/json" else "text/plain")
            responseBuilder.body(body)
        }
        val exchangeFunction = ExchangeFunction { Mono.just(responseBuilder.build()) }
        val tokenUtil = mockk<TokenUtil>()
        every { tokenUtil.getOnBehalfOfTokenWithDokDistKanalScope() } returns "synthetic-on-behalf-token"
        every { tokenUtil.getAppAccessTokenWithDokDistKanalScope() } returns "synthetic-app-token"

        return DokDistKanalClient(
            dokDistKanalWebClient = WebClient.builder().exchangeFunction(exchangeFunction).build(),
            tokenUtil = tokenUtil,
        )
    }

    private fun DokDistKanalClient.get(accessVariant: AccessVariant): BestemDistribusjonskanalResponse =
        when (accessVariant) {
            AccessVariant.ON_BEHALF -> getDistribusjonskanal(request)
            AccessVariant.APP_ACCESS -> getDistribusjonskanalWithAppAccess(request)
        }

    enum class AccessVariant {
        ON_BEHALF,
        APP_ACCESS,
    }

    companion object {
        private val request =
            DokDistKanalClient.Request(
                mottakerId = "synthetic-recipient",
                brukerId = "synthetic-user",
                tema = "synthetic-topic",
            )

        private val noDistributionResponse =
            BestemDistribusjonskanalResponse(
                distribusjonskanal = DistribusjonKanalCode.INGEN_DISTRIBUSJON,
                regel = "",
                regelBegrunnelse = "",
            )

        private val successResponse =
            """
            {
              "distribusjonskanal": "DITT_NAV",
              "regel": "synthetic-rule",
              "regelBegrunnelse": "synthetic-reason"
            }
            """.trimIndent()
    }
}
