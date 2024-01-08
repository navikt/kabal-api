package no.nav.klage.oppgave.service

import com.ninjasquad.springmockk.MockkBean
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.clients.kabaljsontopdf.KabalJsonToPdfClient
import no.nav.klage.dokument.clients.kabalsmarteditorapi.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.repositories.*
import no.nav.klage.dokument.service.*
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentMapper
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.klage.BehandlingRole.KABAL_SAKSBEHANDLING
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import java.util.*


@ActiveProfiles("local")
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DokumentUnderArbeidServiceTest {
    companion object {
        @Container
        @JvmField
        val postgreSQLContainer: TestPostgresqlContainer = TestPostgresqlContainer.instance
    }

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
    lateinit var kabalDocumentMapper: KabalDocumentMapper
    @MockkBean
    lateinit var eregClient: EregClient
    @MockkBean
    lateinit var innholdsfortegnelseService: InnholdsfortegnelseService
    @MockkBean
    lateinit var safFacade: SafFacade
    @MockkBean
    lateinit var dokumentMapper: DokumentMapper


    lateinit var dokumentUnderArbeidService: DokumentUnderArbeidService


    @BeforeEach
    fun setup() {
        dokumentUnderArbeidService = DokumentUnderArbeidService(
            dokumentUnderArbeidRepository = dokumentUnderArbeidRepository,
            dokumentUnderArbeidCommonService = dokumentUnderArbeidCommonService,
            opplastetDokumentUnderArbeidAsHoveddokumentRepository = opplastetDokumentUnderArbeidAsHoveddokumentRepository,
            opplastetDokumentUnderArbeidAsVedleggRepository = opplastetDokumentUnderArbeidAsVedleggRepository,
            smartDokumentUnderArbeidAsHoveddokumentRepository = smartDokumentUnderArbeidAsHoveddokumentRepository,
            smartDokumentUnderArbeidAsVedleggRepository = smartDokumentUnderArbeidAsVedleggRepository,
            journalfoertDokumentUnderArbeidRepository = journalfoertDokumentUnderArbeidRepository,
            attachmentValidator = attachmentValidator,
            mellomlagerService = mellomlagerService,
            smartEditorApiGateway = smartEditorApiGateway,
            kabalJsonToPdfClient = kabalJsonToPdfClient,
            behandlingService = behandlingService,
            kabalDocumentGateway = kabalDocumentGateway,
            applicationEventPublisher = applicationEventPublisher,
            innloggetSaksbehandlerService = innloggetSaksbehandlerService,
            dokumentService = dokumentService,
            kabalDocumentMapper = kabalDocumentMapper,
            eregClient = eregClient,
            innholdsfortegnelseService = innholdsfortegnelseService,
            safFacade = safFacade,
            dokumentMapper = dokumentMapper,
            systembrukerIdent = "SYSTEMBRUKER",
        )

        val behandlingId = UUID.randomUUID()
        val hovedDokument = OpplastetDokumentUnderArbeidAsHoveddokument(
            id = UUID.fromString("0a57804e-6da4-4e4b-9f74-33e8791dbe7e"),
            mellomlagerId = UUID.randomUUID().toString(),
            mellomlagretDate = LocalDateTime.now(),
            markertFerdig = LocalDateTime.now(),
            size = 1002,
            name = "Vedtak.pdf",
            behandlingId = behandlingId,
            dokumentType = DokumentType.BREV,
            creatorIdent = "null",
            creatorRole = KABAL_SAKSBEHANDLING,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            datoMottatt = null,
        )
        dokumentUnderArbeidRepository.save(hovedDokument)
    }
}