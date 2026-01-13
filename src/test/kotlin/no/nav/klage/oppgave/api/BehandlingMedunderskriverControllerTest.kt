package no.nav.klage.oppgave.api

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.klage.oppgave.api.controller.BehandlingMedunderskriverController
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.put
import java.util.*

@WebMvcTest(BehandlingMedunderskriverController::class)
@ActiveProfiles("local")
class BehandlingMedunderskriverControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var behandlingMapper: BehandlingMapper

    @MockkBean
    lateinit var innloggetSaksbehandlerService: InnloggetSaksbehandlerService

    private val klagebehandlingId = UUID.randomUUID()


    @BeforeEach
    fun setup() {
        every { innloggetSaksbehandlerService.getInnloggetIdent() } returns "B54321"
    }

//    @Test
//    fun `putMedunderskriverident with correct input should return ok`() {
//        every {
//            behandlingService.setMedunderskriverFlowState(
//                any(),
//                any(),
//                any(),
//                any()
//            )
//        } returns MedunderskriverWrapped(
//            modified = klagebehandling.modified,
//            flowState = klagebehandling.medunderskriverFlowState,
//            navIdent = "B54321",
//            )
//        )
//
//        val input = SaksbehandlerInput(
//            "A12345"
//        )
//
//        mockMvc.put("/behandlinger/$klagebehandlingId/medunderskriver") {
//            contentType = MediaType.APPLICATION_JSON
//            content = mapper.writeValueAsString(input)
//            accept = MediaType.APPLICATION_JSON
//        }.andExpect {
//            status { isOk() }
//        }
//    }

    @Test
    fun `putMedunderskriverident with incorrect input should return 400 error`() {
        mockMvc.put("/behandlinger/$klagebehandlingId/medunderskriver") {
        }.andExpect {
            status { is4xxClientError() }
        }
    }

}