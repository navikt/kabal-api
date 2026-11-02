package no.nav.klage.oppgave.config

import com.ninjasquad.springmockk.MockkBean
import no.nav.klage.auth.test.MockAzure
import no.nav.klage.auth.test.MockTokenX
import no.nav.klage.oppgave.api.controller.KodeverkController
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.KodeverkService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The dev-only endpoints are open in dev. Their controllers are not loaded, so an open path gives 404
 * and a protected path gives 401.
 */
@WebMvcTest(KodeverkController::class)
@Import(SecurityConfiguration::class)
@ActiveProfiles("dev")
class DevSecurityConfigurationTest {
    companion object {
        private val mockAzure = MockAzure()
        private val mockTokenX = MockTokenX()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            mockAzure.registerProperties(registry)
            mockTokenX.registerProperties(registry)
        }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var kodeverkService: KodeverkService

    @MockkBean
    private lateinit var innloggetSaksbehandlerService: InnloggetSaksbehandlerService

    @Test
    fun `dev endpoints are open in dev`() {
        mockMvc.perform(get("/internal/dev/token/unprotected")).andExpect(status().isNotFound)
        mockMvc.perform(post("/mockdata/randomklage")).andExpect(status().isNotFound)
        mockMvc.perform(post("/internal/manualfeed")).andExpect(status().isNotFound)
    }

    @Test
    fun `other endpoints still require token in dev`() {
        mockMvc.perform(get("/postinfo")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/internal/manualfeed")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/innsyn/saker")).andExpect(status().isUnauthorized)
    }
}
