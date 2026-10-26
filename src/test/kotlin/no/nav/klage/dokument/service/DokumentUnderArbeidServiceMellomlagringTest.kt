package no.nav.klage.dokument.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.SmartDocumentResponse
import no.nav.klage.dokument.domain.PDFDocument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Language
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.gateway.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.api.view.DokumentReferanse
import no.nav.klage.oppgave.domain.behandling.BehandlingRole.KABAL_SAKSBEHANDLING
import no.nav.klage.oppgave.service.BehandlingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.ByteArrayResource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class DokumentUnderArbeidServiceMellomlagringTest {
    private val dokumentUnderArbeidRepository = mockk<DokumentUnderArbeidRepository>()
    private val mellomlagerService = mockk<MellomlagerService>()
    private val smartEditorApiGateway = mockk<DefaultKabalSmartEditorApiGateway>()
    private val behandlingService = mockk<BehandlingService>()
    private val kabalJsonToPdfService = mockk<KabalJsonToPdfService>()
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)

    private lateinit var service: DokumentUnderArbeidService

    private val dokumentId = UUID.randomUUID()
    private val smartEditorId = UUID.randomUUID()
    private val pdfBytes = "pdf".toByteArray()

    @BeforeEach
    fun setup() {
        service =
            DokumentUnderArbeidService(
                dokumentUnderArbeidRepository = dokumentUnderArbeidRepository,
                dokumentUnderArbeidCommonService = mockk(),
                opplastetDokumentUnderArbeidAsHoveddokumentRepository = mockk(),
                opplastetDokumentUnderArbeidAsVedleggRepository = mockk(),
                smartDokumentUnderArbeidAsHoveddokumentRepository = mockk(),
                smartDokumentUnderArbeidAsVedleggRepository = mockk(),
                journalfoertDokumentUnderArbeidRepository = mockk(),
                mellomlagerService = mellomlagerService,
                smartEditorApiGateway = smartEditorApiGateway,
                behandlingService = behandlingService,
                kabalDocumentGateway = mockk(),
                applicationEventPublisher = mockk(),
                innloggetSaksbehandlerService = mockk(),
                dokumentService = mockk(),
                innholdsfortegnelseService = mockk(),
                safFacade = mockk(),
                dokumentMapper = mockk(),
                kafkaInternalEventService = mockk(),
                saksbehandlerService = mockk(),
                partSearchService = mockk(),
                meterRegistry = SimpleMeterRegistry(),
                kodeverkService = mockk(),
                dokDistKanalService = mockk(),
                kabalJsonToPdfService = kabalJsonToPdfService,
                tokenUtil = mockk(),
                innsynsbegjaeringTemplateId = "templateId",
                organisasjonsnummerTrygderetten = "123456789",
                activeSpringProfile = "dev",
                documentPolicyService = mockk(),
                transactionTemplate = TransactionTemplate(transactionManager),
            )

        every { dokumentUnderArbeidRepository.findById(dokumentId) } returns Optional.of(smartdokument(mellomlagerId = OLD_ID))
        every { behandlingService.getBehandlingAndCheckReadAccessToSak(any()) } returns mockk()
        every { smartEditorApiGateway.getSmartDocumentResponse(smartEditorId) } returns smartDocumentResponse()
        every { kabalJsonToPdfService.getPDFDocument(any()) } returns PDFDocument(filename = "doc.pdf", bytes = pdfBytes)
        every { mellomlagerService.uploadResource(any()) } returns NEW_ID
        every { mellomlagerService.deleteDocument(mellomlagerId = any(), systemContext = any()) } just Runs
    }

    @Test
    fun `stores new reference and deletes old file after commit`() {
        val current = smartdokument(mellomlagerId = OLD_ID)
        every { dokumentUnderArbeidRepository.findByIdForUpdate(dokumentId) } returns current

        val pdf = getPdf()

        assertThat(pdf.byteArray).isEqualTo(pdfBytes)
        assertThat(current.mellomlagerId).isEqualTo(NEW_ID)
        assertThat(current.mellomlagretVersion).isEqualTo(VERSION)
        assertThat(current.size).isEqualTo(pdfBytes.size.toLong())
        assertThat(current.mellomlagretDate).isNotNull
        verifyOrder {
            transactionManager.commit(any())
            mellomlagerService.deleteDocument(mellomlagerId = OLD_ID, systemContext = any())
        }
        verify(exactly = 0) { mellomlagerService.deleteDocument(mellomlagerId = NEW_ID, systemContext = any()) }
    }

    @Test
    fun `returns pdf and discards upload when document was deleted concurrently`() {
        every { dokumentUnderArbeidRepository.findByIdForUpdate(dokumentId) } returns null

        val pdf = getPdf()

        assertThat(pdf.byteArray).isEqualTo(pdfBytes)
        verify(exactly = 1) { mellomlagerService.deleteDocument(mellomlagerId = NEW_ID, systemContext = any()) }
        verify(exactly = 0) { mellomlagerService.deleteDocument(mellomlagerId = OLD_ID, systemContext = any()) }
    }

    @Test
    fun `returns pdf and discards upload when another request stored a newer file`() {
        val current = smartdokument(mellomlagerId = "other")
        every { dokumentUnderArbeidRepository.findByIdForUpdate(dokumentId) } returns current

        val pdf = getPdf()

        assertThat(pdf.byteArray).isEqualTo(pdfBytes)
        assertThat(current.mellomlagerId).isEqualTo("other")
        verify(exactly = 1) { mellomlagerService.deleteDocument(mellomlagerId = NEW_ID, systemContext = any()) }
        verify(exactly = 0) { mellomlagerService.deleteDocument(mellomlagerId = OLD_ID, systemContext = any()) }
        verify(exactly = 0) { mellomlagerService.deleteDocument(mellomlagerId = "other", systemContext = any()) }
    }

    @Test
    fun `discards upload and rethrows when storing fails`() {
        every { dokumentUnderArbeidRepository.findByIdForUpdate(dokumentId) } throws RuntimeException("db down")

        assertThrows<RuntimeException> { getPdf() }

        verify(exactly = 1) { transactionManager.rollback(any()) }
        verify(exactly = 1) { mellomlagerService.deleteDocument(mellomlagerId = NEW_ID, systemContext = any()) }
        verify(exactly = 0) { mellomlagerService.deleteDocument(mellomlagerId = OLD_ID, systemContext = any()) }
    }

    private fun getPdf(): ByteArrayResource =
        service
            .getFysiskDokumentAsResourceOrUrl(
                behandlingId = UUID.randomUUID(),
                dokumentId = dokumentId,
                innloggetIdent = "Z123456",
                variantFormat = DokumentReferanse.Variant.Format.ARKIV,
                contentDisposition = "inline",
            ).second as ByteArrayResource

    private fun smartdokument(mellomlagerId: String?) =
        SmartdokumentUnderArbeidAsHoveddokument(
            id = dokumentId,
            size = 1,
            smartEditorId = smartEditorId,
            smartEditorTemplateId = "template",
            mellomlagerId = mellomlagerId,
            mellomlagretDate = null,
            language = Language.NB,
            mellomlagretVersion = null,
            name = "Vedtak",
            behandlingId = UUID.randomUUID(),
            creatorIdent = "Z123456",
            creatorRole = KABAL_SAKSBEHANDLING,
            dokumentType = DokumentType.BREV,
            journalfoerendeEnhetId = null,
        )

    private fun smartDocumentResponse() =
        SmartDocumentResponse(
            documentId = smartEditorId,
            json = "{}",
            data = null,
            version = VERSION,
            authorNavIdent = null,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
        )

    companion object {
        private const val OLD_ID = "old-mellomlager-id"
        private const val NEW_ID = "new-mellomlager-id"
        private const val VERSION = 7
    }
}
