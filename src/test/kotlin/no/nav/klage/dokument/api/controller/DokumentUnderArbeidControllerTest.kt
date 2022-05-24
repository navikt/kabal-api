package no.nav.klage.dokument.api.controller

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import no.nav.klage.dokument.api.mapper.DokumentInputMapper
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.DokumentView
import no.nav.klage.dokument.api.view.SmartHovedDokumentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.KabalSmartEditorApiClient
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentId
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.BehandlingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDateTime
import java.util.*

@WebMvcTest(DokumentUnderArbeidController::class, SmartEditorController::class)
@ActiveProfiles("local")
internal class DokumentUnderArbeidControllerTest {

    @MockkBean
    private lateinit var innloggetSaksbehandlerService: InnloggetSaksbehandlerRepository

    @MockkBean
    private lateinit var dokumentUnderArbeidRepository: DokumentUnderArbeidRepository

    @MockkBean
    private lateinit var behandlingService: BehandlingService

    @MockkBean
    private lateinit var kabalSmartEditorApiClient: KabalSmartEditorApiClient

    @MockkBean
    private lateinit var dokumentUnderArbeidService: DokumentUnderArbeidService

    @SpykBean
    private lateinit var dokumentMapper: DokumentMapper

    @SpykBean
    private lateinit var dokumentInputMapper: DokumentInputMapper

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    @Test
    fun createAndUploadHoveddokument() {

        val behandlingId = UUID.randomUUID()

        every { innloggetSaksbehandlerService.getInnloggetIdent() } returns "IDENT"
        every {
            dokumentUnderArbeidService.opprettOgMellomlagreNyttHoveddokument(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns DokumentUnderArbeid(
            mellomlagerId = "mellomlagerId",
            opplastet = LocalDateTime.now(),
            size = 1001,
            name = "vedtak.pdf",
            behandlingId = behandlingId,
            smartEditorId = null,
            smartEditorTemplateId = null,
            dokumentType = DokumentType.BREV,
            markertFerdig = null,
            ferdigstilt = null,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            dokumentEnhetId = null,
            parentId = null,
            id = DokumentId(UUID.randomUUID())
        )


        val file =
            MockMultipartFile("file", "file-name.pdf", "application/pdf", "whatever".toByteArray())

        val json = mockMvc.perform(
            MockMvcRequestBuilders.multipart("/behandlinger/$behandlingId/dokumenter/fil")
                .file(file)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn().response.contentAsString

        val hovedDokumentView = objectMapper.readValue(json, DokumentView::class.java)
        assertThat(hovedDokumentView).isNotNull
        assertThat(hovedDokumentView.dokumentTypeId).isEqualTo(DokumentType.BREV.id)
    }

    @Test
    fun createSmartEditorHoveddokument() {
        val behandlingId = UUID.randomUUID()
        val smartHovedDokumentInput =
            SmartHovedDokumentInput(
                json = null,
                content = jacksonObjectMapper().readTree("{ \"json\": \"is cool\" }"),
                tittel = "Tittel",
                templateId = "template",
            )

        every { innloggetSaksbehandlerService.getInnloggetIdent() } returns "IDENT"
        every {
            dokumentUnderArbeidService.opprettOgMellomlagreNyttHoveddokument(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } returns DokumentUnderArbeid(
            mellomlagerId = "mellomlagerId",
            opplastet = LocalDateTime.now(),
            size = 1001,
            name = "vedtak.pdf",
            behandlingId = behandlingId,
            smartEditorId = UUID.randomUUID(),
            smartEditorTemplateId = "template",
            dokumentType = DokumentType.BREV,
            markertFerdig = null,
            ferdigstilt = null,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            dokumentEnhetId = null,
            parentId = null,
            id = DokumentId(UUID.randomUUID())
        )


        val json = mockMvc.perform(
            MockMvcRequestBuilders.post("/behandlinger/$behandlingId/dokumenter/smart")
                .content(objectMapper.writeValueAsString(smartHovedDokumentInput))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andDo(MockMvcResultHandlers.print())
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn().response.contentAsString

        val hovedDokumentView = objectMapper.readValue(json, DokumentView::class.java)
        assertThat(hovedDokumentView).isNotNull
        assertThat(hovedDokumentView.dokumentTypeId).isEqualTo(DokumentType.BREV.id)
    }
}