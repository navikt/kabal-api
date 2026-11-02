package no.nav.klage.oppgave.config

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.klage.auth.test.AzureSecurityContractTest
import no.nav.klage.auth.test.MockTokenX
import no.nav.klage.innsyn.api.controller.InnsynController
import no.nav.klage.innsyn.api.view.InnsynResponse
import no.nav.klage.innsyn.service.InnsynService
import no.nav.klage.oppgave.api.controller.KodeverkController
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.KodeverkService
import no.nav.klage.oppgave.util.TokenUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(KodeverkController::class, InnsynController::class)
@Import(SecurityConfiguration::class)
class SecurityConfigurationTest : AzureSecurityContractTest() {
    companion object {
        private val mockTokenX = MockTokenX()

        @JvmStatic
        @DynamicPropertySource
        fun tokenXProperties(registry: DynamicPropertyRegistry) {
            mockTokenX.registerProperties(registry)
        }

        private const val TOKEN_X_PATH = "/api/innsyn/saker"
    }

    @MockkBean
    private lateinit var kodeverkService: KodeverkService

    @MockkBean
    private lateinit var innloggetSaksbehandlerService: InnloggetSaksbehandlerService

    @MockkBean
    private lateinit var innsynService: InnsynService

    @MockkBean
    private lateinit var tokenUtil: TokenUtil

    override fun protectedRequest(): MockHttpServletRequestBuilder = get("/postinfo")

    @BeforeEach
    fun setup() {
        every { innloggetSaksbehandlerService.getInnloggetIdent() } returns "Z123456"
        every { kodeverkService.getPostInfo() } returns emptyList()
        every { tokenUtil.getSubjectFromTokenXToken() } returns "12345678910"
        every { innsynService.getSakerForBruker(fnr = any()) } returns InnsynResponse(saker = emptyList())
    }

    @Test
    fun `innsyn accepts TokenX token`() {
        mockMvc.perform(get(TOKEN_X_PATH).bearer(mockTokenX.token())).andExpect(status().isOk)
    }

    @Test
    fun `innsyn rejects request without token`() {
        mockMvc.perform(get(TOKEN_X_PATH)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `innsyn rejects TokenX token with lower login level`() {
        mockMvc.perform(get(TOKEN_X_PATH).bearer(mockTokenX.token(acr = "idporten-loa-substantial"))).andExpect(status().isUnauthorized)
    }

    @Test
    fun `innsyn rejects Azure token`() {
        mockMvc.perform(get(TOKEN_X_PATH).bearer(mockAzure.token())).andExpect(status().isUnauthorized)
    }

    @Test
    fun `Azure endpoints reject TokenX token`() {
        mockMvc.perform(protectedRequest().bearer(mockTokenX.token())).andExpect(status().isUnauthorized)
    }

    @Test
    fun `dev endpoints require token outside dev`() {
        mockMvc.perform(get("/internal/dev/token/unprotected")).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/mockdata/randomklage")).andExpect(status().isUnauthorized)
        mockMvc.perform(post("/internal/manualfeed")).andExpect(status().isUnauthorized)
    }

    private fun MockHttpServletRequestBuilder.bearer(token: String) = header(HttpHeaders.AUTHORIZATION, "Bearer $token")
}
