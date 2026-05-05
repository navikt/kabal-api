package no.nav.klage.oppgave.service

import com.ninjasquad.springmockk.MockkBean
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.clients.kabaljsontopdf.KabalJsonToPdfClient
import no.nav.klage.dokument.domain.dokumenterunderarbeid.JournalfoertDokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.gateway.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.repositories.*
import no.nav.klage.dokument.service.*
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.kabaldocument.model.response.DokumentEnhetFullfoerOutput
import no.nav.klage.oppgave.clients.kabaldocument.model.response.JoarkReference
import no.nav.klage.oppgave.clients.kabaldocument.model.response.SourceReferenceWithJoarkReferences
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.clients.saf.graphql.*
import no.nav.klage.oppgave.db.PostgresIntegrationTestBase
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingRole.KABAL_SAKSBEHANDLING
import no.nav.klage.oppgave.domain.behandling.embedded.Ferdigstilling
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.behandling.setters.BehandlingSetters
import no.nav.klage.oppgave.domain.behandling.subentities.Saksdokument
import no.nav.klage.oppgave.domain.kafka.InternalBehandlingEvent
import no.nav.klage.oppgave.domain.kafka.InternalEventType
import no.nav.klage.oppgave.util.TokenUtil
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import java.util.*

@ActiveProfiles("local")
@DataJpaTest
class DokumentUnderArbeidServiceWithJPATest : PostgresIntegrationTestBase() {

    @Autowired
    lateinit var dokumentUnderArbeidRepository: DokumentUnderArbeidRepository

    @MockkBean
    lateinit var dokumentUnderArbeidCommonService: DokumentUnderArbeidCommonService

    @MockkBean
    lateinit var opplastetDokumentUnderArbeidAsHoveddokumentRepository: OpplastetDokumentUnderArbeidAsHoveddokumentRepository

    @MockkBean
    lateinit var opplastetDokumentUnderArbeidAsVedleggRepository: OpplastetDokumentUnderArbeidAsVedleggRepository

    @MockkBean
    lateinit var smartDokumentUnderArbeidAsHoveddokumentRepository: SmartdokumentUnderArbeidAsHoveddokumentRepository

    @MockkBean
    lateinit var smartDokumentUnderArbeidAsVedleggRepository: SmartdokumentUnderArbeidAsVedleggRepository

    @MockkBean
    lateinit var journalfoertDokumentUnderArbeidRepository: JournalfoertDokumentUnderArbeidAsVedleggRepository

    @MockkBean
    lateinit var attachmentValidator: MellomlagretDokumentValidatorService

    @MockkBean
    lateinit var mellomlagerService: MellomlagerService

    @MockkBean
    lateinit var smartEditorApiGateway: DefaultKabalSmartEditorApiGateway

    @MockkBean
    lateinit var kabalJsonToPdfClient: KabalJsonToPdfClient

    @MockkBean
    lateinit var behandlingService: BehandlingService

    @MockkBean
    lateinit var kabalDocumentGateway: KabalDocumentGateway

    @MockkBean
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockkBean
    lateinit var innloggetSaksbehandlerService: InnloggetSaksbehandlerService

    @MockkBean
    lateinit var dokumentService: DokumentService

    @MockkBean
    lateinit var innholdsfortegnelseService: InnholdsfortegnelseService

    @MockkBean
    lateinit var safFacade: SafFacade

    @MockkBean
    lateinit var dokumentMapper: DokumentMapper

    @MockkBean
    lateinit var tokenUtilBean: TokenUtil

    lateinit var kafkaInternalEventService: KafkaInternalEventService
    lateinit var saksbehandlerService: SaksbehandlerService


    lateinit var dokumentUnderArbeidService: DokumentUnderArbeidService


    @BeforeEach
    fun setup() {
        dokumentUnderArbeidRepository.deleteAll()

        kafkaInternalEventService = mockk(relaxed = true)
        saksbehandlerService = mockk(relaxed = true)
        justRun { applicationEventPublisher.publishEvent(any<Any>()) }

        dokumentUnderArbeidService = DokumentUnderArbeidService(
            dokumentUnderArbeidRepository = dokumentUnderArbeidRepository,
            dokumentUnderArbeidCommonService = dokumentUnderArbeidCommonService,
            opplastetDokumentUnderArbeidAsHoveddokumentRepository = opplastetDokumentUnderArbeidAsHoveddokumentRepository,
            opplastetDokumentUnderArbeidAsVedleggRepository = opplastetDokumentUnderArbeidAsVedleggRepository,
            smartDokumentUnderArbeidAsHoveddokumentRepository = smartDokumentUnderArbeidAsHoveddokumentRepository,
            smartDokumentUnderArbeidAsVedleggRepository = smartDokumentUnderArbeidAsVedleggRepository,
            journalfoertDokumentUnderArbeidRepository = journalfoertDokumentUnderArbeidRepository,
            mellomlagerService = mellomlagerService,
            smartEditorApiGateway = smartEditorApiGateway,
            behandlingService = behandlingService,
            kabalDocumentGateway = kabalDocumentGateway,
            applicationEventPublisher = applicationEventPublisher,
            innloggetSaksbehandlerService = innloggetSaksbehandlerService,
            dokumentService = dokumentService,
            innholdsfortegnelseService = innholdsfortegnelseService,
            safFacade = safFacade,
            dokumentMapper = dokumentMapper,
            kafkaInternalEventService = kafkaInternalEventService,
            saksbehandlerService = saksbehandlerService,
            meterRegistry = SimpleMeterRegistry(),
            partSearchService = mockk(),
            kodeverkService = mockk(),
            dokDistKanalService = mockk(),
            kabalJsonToPdfService = mockk(),
            tokenUtil = mockk(),
            innsynsbegjaeringTemplateId = "templateId",
            organisasjonsnummerTrygderetten = "123456789",
            activeSpringProfile = "dev",
            documentPolicyService = mockk(),
        )
    }

    @Test
    fun `ferdigstillDokumentEnhet finalizes hoveddokument and vedlegg and persists dokarkiv references`() {
        val behandlingId = UUID.randomUUID()
        val dokumentEnhetId = UUID.randomUUID()
        val hovedDokument = saveHoveddokument(behandlingId = behandlingId, dokumentEnhetId = dokumentEnhetId)
        val vedlegg = saveVedlegg(behandlingId = behandlingId, parentId = hovedDokument.id)

        mockkObject(BehandlingSetters)

        every { dokumentUnderArbeidCommonService.findVedleggByParentId(hovedDokument.id) } returns setOf(vedlegg)
        every { kabalDocumentGateway.fullfoerDokumentEnhet(dokumentEnhetId) } returns DokumentEnhetFullfoerOutput(
            sourceReferenceWithJoarkReferencesList = listOf(
                SourceReferenceWithJoarkReferences(
                    sourceReference = hovedDokument.id,
                    joarkReferenceList = listOf(JoarkReference(journalpostId = "jp-1", dokumentInfoId = "doc-1")),
                ),
                SourceReferenceWithJoarkReferences(
                    sourceReference = vedlegg.id,
                    joarkReferenceList = listOf(JoarkReference(journalpostId = "jp-1", dokumentInfoId = "doc-2")),
                ),
            )
        )

        val behandling = createBehandling(ferdigstilling = null)
        every { behandlingService.getBehandlingForReadWithoutCheckForAccess(behandlingId) } returns behandling
        every {
            safFacade.getJournalposter(
                journalpostIdSet = setOf("jp-1"),
                fnr = "12345678910",
                saksbehandlerContext = false,
            )
        } returns listOf(createJournalpost(journalpostId = "jp-1", dokumentInfoIds = listOf("doc-1", "doc-2")))
        every { saksbehandlerService.getNameForIdentDefaultIfNull("Z123") } returns "Saks Behandler"
        with(BehandlingSetters) {
            every { behandling.addSaksdokument(any(), any()) } answers { callOriginal() }
        }

        try {
            val result = dokumentUnderArbeidService.ferdigstillDokumentEnhet(hovedDokument.id)

            val refreshedHoved = dokumentUnderArbeidRepository.findById(hovedDokument.id)
                .get() as OpplastetDokumentUnderArbeidAsHoveddokument
            val refreshedVedlegg =
                dokumentUnderArbeidRepository.findById(vedlegg.id).get() as OpplastetDokumentUnderArbeidAsVedlegg

            assertEquals(hovedDokument.id, result.id)
            assertNotNull(refreshedHoved.ferdigstilt)
            assertNotNull(refreshedVedlegg.ferdigstilt)
            assertTrue(
                refreshedHoved.dokarkivReferences.any { it.journalpostId == "jp-1" && it.dokumentInfoId == "doc-1" },
                "Hoveddokument should get joark reference from sourceReference output",
            )
            assertTrue(
                refreshedVedlegg.dokarkivReferences.any { it.journalpostId == "jp-1" && it.dokumentInfoId == "doc-2" },
                "Vedlegg should get joark reference from sourceReference output",
            )

            verify(exactly = 1) { kabalDocumentGateway.fullfoerDokumentEnhet(dokumentEnhetId) }
            verify(exactly = 1) {
                safFacade.getJournalposter(
                    journalpostIdSet = setOf("jp-1"),
                    fnr = "12345678910",
                    saksbehandlerContext = false,
                )
            }
            with(BehandlingSetters) {
                verify(exactly = 1) {
                    behandling.addSaksdokument(
                        match<Saksdokument> { it.journalpostId == "jp-1" && it.dokumentInfoId == "doc-1" },
                        "Z123",
                    )
                }
                verify(exactly = 1) {
                    behandling.addSaksdokument(
                        match<Saksdokument> { it.journalpostId == "jp-1" && it.dokumentInfoId == "doc-2" },
                        "Z123",
                    )
                }
            }
            verify(exactly = 1) { kafkaInternalEventService.publishInternalBehandlingEvent(match { it.type == InternalEventType.INCLUDED_DOCUMENTS_ADDED }) }
            verify(atLeast = 1) { applicationEventPublisher.publishEvent(any<Any>()) }
        } finally {
            unmockkObject(BehandlingSetters)
        }
    }

    @Test
    fun `ferdigstillDokumentEnhet does not add saksdokumenter or publish included-documents event when behandling is finalized`() {
        val behandlingId = UUID.randomUUID()
        val dokumentEnhetId = UUID.randomUUID()
        val hovedDokument = saveHoveddokument(behandlingId = behandlingId, dokumentEnhetId = dokumentEnhetId)

        mockkObject(BehandlingSetters)

        every { dokumentUnderArbeidCommonService.findVedleggByParentId(hovedDokument.id) } returns emptySet()
        every { kabalDocumentGateway.fullfoerDokumentEnhet(dokumentEnhetId) } returns DokumentEnhetFullfoerOutput(
            sourceReferenceWithJoarkReferencesList = listOf(
                SourceReferenceWithJoarkReferences(
                    sourceReference = hovedDokument.id,
                    joarkReferenceList = listOf(JoarkReference(journalpostId = "jp-2", dokumentInfoId = "doc-2")),
                )
            )
        )

        val ferdigstilling = Ferdigstilling(
            avsluttet = LocalDateTime.now(),
            avsluttetAvSaksbehandler = LocalDateTime.now(),
            navIdent = "A123",
            navn = "Ferdig Saksbehandler",
        )
        every { behandlingService.getBehandlingForReadWithoutCheckForAccess(behandlingId) } returns createBehandling(
            ferdigstilling
        )
        every {
            safFacade.getJournalposter(
                journalpostIdSet = setOf("jp-2"),
                fnr = "12345678910",
                saksbehandlerContext = false,
            )
        } returns listOf(createJournalpost(journalpostId = "jp-2", dokumentInfoIds = listOf("doc-2")))
        with(BehandlingSetters) {
            every { any<Behandling>().addSaksdokument(any(), any()) } answers { callOriginal() }
        }

        try {
            dokumentUnderArbeidService.ferdigstillDokumentEnhet(hovedDokument.id)

            verify(exactly = 0) { kafkaInternalEventService.publishInternalBehandlingEvent(any<InternalBehandlingEvent>()) }
            verify(exactly = 0) { applicationEventPublisher.publishEvent(any<Any>()) }
            with(BehandlingSetters) {
                verify(exactly = 0) {
                    any<Behandling>().addSaksdokument(any<Saksdokument>(), any())
                }
            }
        } finally {
            unmockkObject(BehandlingSetters)
        }
    }

    @Test
    fun `ferdigstillDokumentEnhet deduplicates journalpost ids and skips null sourceReference when writing dokarkiv references`() {
        val behandlingId = UUID.randomUUID()
        val dokumentEnhetId = UUID.randomUUID()
        val hovedDokument = saveHoveddokument(behandlingId = behandlingId, dokumentEnhetId = dokumentEnhetId)

        mockkObject(BehandlingSetters)

        every { dokumentUnderArbeidCommonService.findVedleggByParentId(hovedDokument.id) } returns emptySet()
        every { kabalDocumentGateway.fullfoerDokumentEnhet(dokumentEnhetId) } returns DokumentEnhetFullfoerOutput(
            sourceReferenceWithJoarkReferencesList = listOf(
                SourceReferenceWithJoarkReferences(
                    sourceReference = null,
                    joarkReferenceList = listOf(JoarkReference(journalpostId = "jp-3", dokumentInfoId = "doc-null")),
                ),
                SourceReferenceWithJoarkReferences(
                    sourceReference = hovedDokument.id,
                    joarkReferenceList = listOf(
                        JoarkReference(journalpostId = "jp-3", dokumentInfoId = "doc-3"),
                        JoarkReference(journalpostId = "jp-3", dokumentInfoId = "doc-4"),
                    ),
                ),
            )
        )

        every { behandlingService.getBehandlingForReadWithoutCheckForAccess(behandlingId) } returns createBehandling(
            ferdigstilling = null
        )
        every {
            safFacade.getJournalposter(
                journalpostIdSet = setOf("jp-3"),
                fnr = "12345678910",
                saksbehandlerContext = false,
            )
        } returns listOf(
            createJournalpost(
                journalpostId = "jp-3",
                dokumentInfoIds = listOf("doc-3", "doc-4", "doc-null")
            )
        )
        every { saksbehandlerService.getNameForIdentDefaultIfNull("Z123") } returns "Saks Behandler"
        val behandling = createBehandling(ferdigstilling = null)
        every { behandlingService.getBehandlingForReadWithoutCheckForAccess(behandlingId) } returns behandling
        with(BehandlingSetters) {
            every { behandling.addSaksdokument(any(), any()) } answers { callOriginal() }
        }

        try {
            dokumentUnderArbeidService.ferdigstillDokumentEnhet(hovedDokument.id)

            val refreshedHoved = dokumentUnderArbeidRepository.findById(hovedDokument.id)
                .get() as OpplastetDokumentUnderArbeidAsHoveddokument

            assertTrue(refreshedHoved.dokarkivReferences.none { it.dokumentInfoId == "doc-null" })
            assertTrue(refreshedHoved.dokarkivReferences.any { it.dokumentInfoId == "doc-3" })
            assertTrue(refreshedHoved.dokarkivReferences.any { it.dokumentInfoId == "doc-4" })

            verify(exactly = 1) {
                safFacade.getJournalposter(
                    journalpostIdSet = setOf("jp-3"),
                    fnr = "12345678910",
                    saksbehandlerContext = false,
                )
            }
            with(BehandlingSetters) {
                verify(exactly = 1) {
                    behandling.addSaksdokument(
                        match<Saksdokument> { it.journalpostId == "jp-3" && it.dokumentInfoId == "doc-3" },
                        "Z123",
                    )
                }
                verify(exactly = 1) {
                    behandling.addSaksdokument(
                        match<Saksdokument> { it.journalpostId == "jp-3" && it.dokumentInfoId == "doc-4" },
                        "Z123",
                    )
                }
                verify(exactly = 1) {
                    behandling.addSaksdokument(
                        match<Saksdokument> { it.journalpostId == "jp-3" && it.dokumentInfoId == "doc-null" },
                        "Z123",
                    )
                }
            }
        } finally {
            unmockkObject(BehandlingSetters)
        }
    }

    @Test
    fun `ferdigstillDokumentEnhet adds dokarkiv refs for journalfoert source but skips included-document side effects`() {
        val behandlingId = UUID.randomUUID()
        val dokumentEnhetId = UUID.randomUUID()
        val hovedDokument = saveHoveddokument(behandlingId = behandlingId, dokumentEnhetId = dokumentEnhetId)
        val journalfoertVedlegg = saveJournalfoertVedlegg(behandlingId = behandlingId, parentId = hovedDokument.id)

        mockkObject(BehandlingSetters)

        every { dokumentUnderArbeidCommonService.findVedleggByParentId(hovedDokument.id) } returns emptySet()
        every { kabalDocumentGateway.fullfoerDokumentEnhet(dokumentEnhetId) } returns DokumentEnhetFullfoerOutput(
            sourceReferenceWithJoarkReferencesList = listOf(
                SourceReferenceWithJoarkReferences(
                    sourceReference = journalfoertVedlegg.id,
                    joarkReferenceList = listOf(JoarkReference(journalpostId = "jp-jf", dokumentInfoId = "doc-jf")),
                ),
            )
        )

        every { behandlingService.getBehandlingForReadWithoutCheckForAccess(behandlingId) } returns createBehandling(
            ferdigstilling = null
        )
        every {
            safFacade.getJournalposter(
                journalpostIdSet = setOf("jp-jf"),
                fnr = "12345678910",
                saksbehandlerContext = false,
            )
        } returns listOf(createJournalpost(journalpostId = "jp-jf", dokumentInfoIds = listOf("doc-jf")))
        with(BehandlingSetters) {
            every { any<Behandling>().addSaksdokument(any(), any()) } answers { callOriginal() }
        }

        try {
            dokumentUnderArbeidService.ferdigstillDokumentEnhet(hovedDokument.id)

            val refreshedJournalfoertVedlegg =
                dokumentUnderArbeidRepository.findById(journalfoertVedlegg.id)
                    .get() as JournalfoertDokumentUnderArbeidAsVedlegg

            assertTrue(
                refreshedJournalfoertVedlegg.dokarkivReferences.any {
                    it.journalpostId == "jp-jf" && it.dokumentInfoId == "doc-jf"
                },
                "Journalfoert source document should still get dokarkiv references",
            )

            verify(exactly = 0) { kafkaInternalEventService.publishInternalBehandlingEvent(any<InternalBehandlingEvent>()) }
            verify(exactly = 0) { applicationEventPublisher.publishEvent(any<Any>()) }
            with(BehandlingSetters) {
                verify(exactly = 0) {
                    any<Behandling>().addSaksdokument(any<Saksdokument>(), any())
                }
            }
        } finally {
            unmockkObject(BehandlingSetters)
        }
    }

    private fun saveHoveddokument(
        behandlingId: UUID,
        dokumentEnhetId: UUID
    ): OpplastetDokumentUnderArbeidAsHoveddokument {
        return dokumentUnderArbeidRepository.save(
            OpplastetDokumentUnderArbeidAsHoveddokument(
                id = UUID.randomUUID(),
                mellomlagerId = UUID.randomUUID().toString(),
                mellomlagretDate = LocalDateTime.now(),
                markertFerdig = LocalDateTime.now().minusMinutes(2),
                markertFerdigBy = "Z123",
                size = 1002,
                name = "Vedtak.pdf",
                behandlingId = behandlingId,
                dokumentType = DokumentType.BREV,
                creatorIdent = "Z123",
                creatorRole = KABAL_SAKSBEHANDLING,
                created = LocalDateTime.now().minusMinutes(5),
                modified = LocalDateTime.now().minusMinutes(2),
                datoMottatt = null,
                journalfoerendeEnhetId = null,
                inngaaendeKanal = null,
                dokumentEnhetId = dokumentEnhetId,
            )
        )
    }

    private fun saveVedlegg(behandlingId: UUID, parentId: UUID): OpplastetDokumentUnderArbeidAsVedlegg {
        return dokumentUnderArbeidRepository.save(
            OpplastetDokumentUnderArbeidAsVedlegg(
                id = UUID.randomUUID(),
                size = 120,
                mellomlagerId = UUID.randomUUID().toString(),
                mellomlagretDate = LocalDateTime.now(),
                markertFerdig = LocalDateTime.now().minusMinutes(2),
                markertFerdigBy = "Z123",
                name = "Vedlegg.pdf",
                behandlingId = behandlingId,
                parentId = parentId,
                creatorIdent = "Z123",
                creatorRole = KABAL_SAKSBEHANDLING,
                created = LocalDateTime.now().minusMinutes(4),
                modified = LocalDateTime.now().minusMinutes(2),
            )
        )
    }

    private fun saveJournalfoertVedlegg(behandlingId: UUID, parentId: UUID): JournalfoertDokumentUnderArbeidAsVedlegg {
        return dokumentUnderArbeidRepository.save(
            JournalfoertDokumentUnderArbeidAsVedlegg(
                id = UUID.randomUUID(),
                opprettet = LocalDateTime.now().minusDays(2),
                journalpostId = "jp-original",
                dokumentInfoId = "doc-original",
                sortKey = "2026-01-01T00:00:00;doc-original",
                name = "Journalfoert vedlegg",
                behandlingId = behandlingId,
                created = LocalDateTime.now().minusDays(1),
                modified = LocalDateTime.now().minusDays(1),
                markertFerdig = LocalDateTime.now().minusHours(1),
                markertFerdigBy = "Z123",
                ferdigstilt = null,
                parentId = parentId,
                creatorIdent = "Z123",
                creatorRole = KABAL_SAKSBEHANDLING,
            )
        )
    }

    private fun createBehandling(ferdigstilling: Ferdigstilling?): Behandling {
        val behandling = mockk<Behandling>(relaxed = true)
        val behandlingId = UUID.randomUUID()
        val saksdokumenter = mutableSetOf<no.nav.klage.oppgave.domain.behandling.subentities.Saksdokument>()
        every { behandling.id } returns behandlingId
        every { behandling.ferdigstilling } returns ferdigstilling
        every { behandling.sakenGjelder } returns SakenGjelder(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.PERSON, value = "12345678910"),
        )
        every { behandling.saksdokumenter } returns saksdokumenter
        every { behandling.modified = any() } returns Unit
        return behandling
    }

    private fun createJournalpost(journalpostId: String, dokumentInfoIds: List<String>): Journalpost {
        return Journalpost(
            journalpostId = journalpostId,
            journalposttype = Journalposttype.I,
            journalstatus = Journalstatus.JOURNALFOERT,
            tema = Tema.GEN,
            sak = null,
            bruker = Bruker(id = "12345678910", type = "FNR"),
            avsenderMottaker = null,
            opprettetAvNavn = null,
            skjerming = null,
            datoOpprettet = LocalDateTime.now().minusDays(2),
            datoSortering = LocalDateTime.now().minusDays(2),
            dokumenter = dokumentInfoIds.map { dokumentInfoId ->
                DokumentInfo(
                    dokumentInfoId = dokumentInfoId,
                    tittel = "Dokument $dokumentInfoId",
                    brevkode = null,
                    skjerming = null,
                    logiskeVedlegg = null,
                    dokumentvarianter = emptyList(),
                    datoFerdigstilt = null,
                    originalJournalpostId = null,
                )
            },
            relevanteDatoer = null,
            kanal = "NAV_NO",
            kanalnavn = "Nav.no",
            utsendingsinfo = null,
        )
    }
}