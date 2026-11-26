package no.nav.klage.oppgave.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.klage.oppgave.api.controller.BehandlingController
import no.nav.klage.oppgave.api.view.TrygderettenSaksnummerInput
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.util.UUID

class BehandlingTrygderettenSaksnummerControllerTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["2026123"])
    fun `setter route accepts a number or null and returns modified`(saksnummer: String?) {
        val service = mockk<BehandlingService>()
        val innloggetSaksbehandlerService = mockk<InnloggetSaksbehandlerService>()
        val behandlingId = UUID.randomUUID()
        val modified = LocalDateTime.of(2026, 6, 15, 12, 34, 56)
        every { innloggetSaksbehandlerService.getInnloggetIdent() } returns "TEST"
        every {
            service.setTrygderettenSaksnummer(
                behandlingId = behandlingId,
                trygderettenSaksnummer = saksnummer,
                utfoerendeSaksbehandlerIdent = "TEST",
            )
        } returns modified
        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    BehandlingController(
                        behandlingService = service,
                        innloggetSaksbehandlerService = innloggetSaksbehandlerService,
                    ),
                ).build()

        mockMvc
            .put("/behandlinger/$behandlingId/trygderettensaksnummer") {
                contentType = MediaType.APPLICATION_JSON
                content = jacksonObjectMapper().writeValueAsString(TrygderettenSaksnummerInput(saksnummer))
            }.andExpect {
                status { isOk() }
                jsonPath("$.modified") { value(modified.toString()) }
            }
        verify(exactly = 1) {
            service.setTrygderettenSaksnummer(
                behandlingId = behandlingId,
                trygderettenSaksnummer = saksnummer,
                utfoerendeSaksbehandlerIdent = "TEST",
            )
        }
    }
}
