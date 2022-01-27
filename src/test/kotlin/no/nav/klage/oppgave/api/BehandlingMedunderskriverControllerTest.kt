package no.nav.klage.oppgave.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.controller.BehandlingMedunderskriverController
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.BehandlingMedunderskriveridentInput
import no.nav.klage.oppgave.api.view.MedunderskriverFlytResponse
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.BehandlingService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.put
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@WebMvcTest(BehandlingMedunderskriverController::class)
@ActiveProfiles("local")
class BehandlingMedunderskriverControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var mapper: ObjectMapper

    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var behandlingMapper: BehandlingMapper

    @MockkBean
    lateinit var innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository

    private val klagebehandlingId = UUID.randomUUID()

    private val klagebehandling = Klagebehandling(
        klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
        sakenGjelder = SakenGjelder(
            partId = PartId(type = PartIdType.PERSON, value = "23452354"),
            skalMottaKopi = false
        ),
        ytelse = Ytelse.OMS_OMP,
        type = Type.KLAGE,
        frist = LocalDate.now(),
        hjemler = mutableSetOf(
            Hjemmel.FTRL_8_7
        ),
        created = LocalDateTime.now(),
        modified = LocalDateTime.now(),
        mottattKlageinstans = LocalDateTime.now(),
        kildesystem = Fagsystem.K9,
        kildeReferanse = "abc",
        mottakId = UUID.randomUUID(),
        delbehandlinger = setOf(
            Delbehandling(
                utfall = Utfall.AVVIST,
                hjemler = mutableSetOf(
                    Registreringshjemmel.ARBML_13
                ),
                medunderskriver = MedunderskriverTildeling(
                    saksbehandlerident = "C78901",
                    tidspunkt = LocalDateTime.now()
                ),
            )
        ),
        mottattFoersteinstans = LocalDate.now(),
        avsenderEnhetFoersteinstans = "0101"
    )

    @BeforeEach
    fun setup() {
        every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns "B54321"
    }

    @Test
    fun `putMedunderskriverident with correct input should return ok`() {
        every {
            behandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                any(),
                any(),
                any(),
                any()
            )
        } returns klagebehandling as Behandling
        every { behandlingMapper.mapToMedunderskriverFlytResponse(klagebehandling as Behandling) } returns MedunderskriverFlytResponse(
            klagebehandling.modified,
            klagebehandling.currentDelbehandling().medunderskriverFlyt
        )

        val input = BehandlingMedunderskriveridentInput(
            "A12345"
        )

        mockMvc.put("/klagebehandlinger/$klagebehandlingId/medunderskriverident") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(input)
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `putMedunderskriverident with incorrect input should return 400 error`() {
        mockMvc.put("/klagebehandlinger/$klagebehandlingId/medunderskriverident") {
        }.andExpect {
            status { is4xxClientError() }
        }
    }

}