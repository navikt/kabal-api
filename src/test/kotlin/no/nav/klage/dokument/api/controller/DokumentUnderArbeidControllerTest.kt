package no.nav.klage.dokument.api.controller

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import no.nav.klage.dokument.api.mapper.DokumentInputMapper
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.clients.kabalsmarteditorapi.KabalSmartEditorApiClient
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@WebMvcTest(DokumentUnderArbeidController::class, SmartEditorController::class)
@ActiveProfiles("local")
internal class DokumentUnderArbeidControllerTest {

    @MockkBean
    private lateinit var innloggetSaksbehandlerService: InnloggetSaksbehandlerService

    @MockkBean
    private lateinit var dokumentUnderArbeidRepository: DokumentUnderArbeidRepository

    @MockkBean
    private lateinit var behandlingService: BehandlingService

    @MockkBean
    private lateinit var kabalSmartEditorApiClient: KabalSmartEditorApiClient

    @MockkBean
    private lateinit var dokumentUnderArbeidService: DokumentUnderArbeidService

    @MockkBean
    private lateinit var safClient: SafGraphQlClient

    @SpykBean
    private lateinit var dokumentMapper: DokumentMapper

    @SpykBean
    private lateinit var dokumentInputMapper: DokumentInputMapper

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = ourJacksonObjectMapper()
    /*
        @Test
        fun createAndUploadHoveddokument() {

            val behandlingId = UUID.randomUUID()

            every { innloggetSaksbehandlerService.getInnloggetIdent() } returns "IDENT"
            every {
                dokumentUnderArbeidService.createOpplastetDokumentUnderArbeid(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns OpplastetDokumentUnderArbeidAsHoveddokument(
                mellomlagerId = "mellomlagerId",
                mellomlagretDate = LocalDateTime.now(),
                size = 1001,
                name = "vedtak.pdf",
                behandlingId = behandlingId,
                dokumentType = DokumentType.BREV,
                markertFerdig = null,
                ferdigstilt = null,
                created = LocalDateTime.now(),
                modified = LocalDateTime.now(),
                id = UUID.randomUUID(),
                creatorIdent = "null",
                creatorRole = BehandlingRole.KABAL_SAKSBEHANDLING,
                datoMottatt = null,
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
            val smartEditorDocumentId = UUID.randomUUID()

            val smartHovedDokumentInput =
                SmartHovedDokumentInput(
                    content = jacksonObjectMapper().readTree("{ \"json\": \"is cool\" }"),
                    tittel = "Tittel",
                    templateId = "template",
                    version = null,
                    parentId = null,
                )

            every { dokumentUnderArbeidService.getSmartEditorId(any(), any()) } returns smartEditorDocumentId
            every { kabalSmartEditorApiClient.getDocument(smartEditorDocumentId) } returns DocumentOutput(
                id = smartEditorDocumentId,
                json = smartHovedDokumentInput.content.toString(),
                created = LocalDateTime.now(),
                modified = LocalDateTime.now(),
            )
            every { innloggetSaksbehandlerService.getInnloggetIdent() } returns "IDENT"

            every {
                dokumentUnderArbeidService.opprettSmartdokument(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } returns SmartdokumentUnderArbeidAsHoveddokument(
                mellomlagerId = "mellomlagerId",
                mellomlagretDate = LocalDateTime.now(),
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
                id = UUID.randomUUID(),
                creatorIdent = "null",
                creatorRole = BehandlingRole.KABAL_SAKSBEHANDLING,
            )

            val json = mockMvc.perform(
                MockMvcRequestBuilders.post("/behandlinger/$behandlingId/smartdokumenter")
                    .content(objectMapper.writeValueAsString(smartHovedDokumentInput))
                    .contentType(MediaType.APPLICATION_JSON)
            )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn().response.contentAsString

            val hovedDokumentView = objectMapper.readValue(json, SmartEditorDocumentView::class.java)
            assertThat(hovedDokumentView).isNotNull
            assertThat(hovedDokumentView.dokumentTypeId).isEqualTo(DokumentType.BREV.id)
        }
        */
}