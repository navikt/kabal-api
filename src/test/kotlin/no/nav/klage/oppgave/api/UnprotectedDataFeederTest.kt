package no.nav.klage.oppgave.api

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.finn.unleash.Unleash
import no.finn.unleash.UnleashContext
import no.nav.klage.oppgave.api.controller.UnprotectedDataFeeder
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.AnkeITrygderettenbehandlingService
import no.nav.klage.oppgave.service.MottakService
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(UnprotectedDataFeeder::class)
@ActiveProfiles("local")
class UnprotectedDataFeederTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var unleash: Unleash

    @MockkBean
    lateinit var innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository

    @MockkBean
    lateinit var mottakService: MottakService

    @MockkBean
    lateinit var ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService

    @BeforeEach
    fun setup() {
        every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns "H149390"
        every { unleash.isEnabled(any(), any<UnleashContext>()) } returns true
        every { mottakService.createMottakForKlageAnkeV3(any()) } returns Unit
    }

    @Test
    fun `tester at format funker`() {
        this.mockMvc.perform(
            post("/internal/manualfeed")
                .content(data)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
    }

    @Language("json")
    private val data = """
        {
          "forrigeBehandlendeEnhet": "4487",
          "fagsak": {
            "fagsakId": "7FH38",
            "fagsystem": "K9"
          },
          "hjemler": ["FTRL_8_3"],
          "innsendtTilNav": "2020-12-17",
          "kilde": "MANUELL",
          "kildeReferanse": "08097520454",
          "klager": {
            "id": {
              "type": "PERSON",
              "verdi": "08097520454"
            }
          },
          "brukersHenvendelseMottattNavDato": "2020-12-20",
          "ytelse": "OMS_OMP",          
          "tilknyttedeJournalposter": [
            {
              "journalpostId": "492889386",
              "type": "BRUKERS_KLAGE"
            }
          ],
          "oversendtKaDato": "2020-12-20T00:00",
          "sakMottattKaTidspunkt": "2020-12-20T00:00",
          "type": "KLAGE"
        }
    """.trimIndent()
}
