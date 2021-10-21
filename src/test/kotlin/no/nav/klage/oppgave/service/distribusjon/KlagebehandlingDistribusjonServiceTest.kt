package no.nav.klage.oppgave.service.distribusjon

import brave.Tracer
import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import no.nav.klage.oppgave.clients.dokdistfordeling.DistribuerJournalpostResponse
import no.nav.klage.oppgave.clients.dokdistfordeling.DokDistFordelingClient
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.joark.JoarkClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.klagefileapi.FileApiClient
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.DokumentInnholdOgTittel
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.kodeverk.*
import no.nav.klage.oppgave.gateway.JournalpostGateway
import no.nav.klage.oppgave.repositories.KafkaVedtakEventRepository
import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import no.nav.klage.oppgave.repositories.MottakRepository
import no.nav.klage.oppgave.service.*
import no.nav.klage.oppgave.util.AttachmentValidator
import no.nav.klage.oppgave.util.PdfUtils
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


@ActiveProfiles("local")
@Import(KlagebehandlingDistribusjonServiceTest.MyTestConfiguration::class)
@SpringBootTest(classes = [KlagebehandlingService::class, VedtakDistribusjonService::class, KlagebehandlingDistribusjonService::class, VedtakService::class, VedtakDistribusjonService::class])
@EnableJpaRepositories(basePackages = ["no.nav.klage.oppgave.repositories"])
@EntityScan("no.nav.klage.oppgave.domain")
@AutoConfigureDataJpa
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
//@AutoConfigureTestEntityManager
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class KlagebehandlingDistribusjonServiceTest {

    companion object {
        @Container
        @JvmField
        val postgreSQLContainer: TestPostgresqlContainer = TestPostgresqlContainer.instance
    }

    @Configuration
    internal class MyTestConfiguration {

        @MockkBean(relaxed = true)
        lateinit var tilgangService: TilgangService

        @MockkBean(relaxed = true)
        lateinit var applicationEventPublisher: ApplicationEventPublisher

        @MockkBean(relaxed = true)
        lateinit var dokumentService: DokumentService

        @MockkBean(relaxed = true)
        lateinit var fileApiClient: FileApiClient

        @MockkBean(relaxed = true)
        lateinit var joarkClient: JoarkClient

        @MockkBean(relaxed = true)
        lateinit var tracer: Tracer

        @MockkBean(relaxed = true)
        lateinit var pdlFacade: PdlFacade

        @MockkBean(relaxed = true)
        lateinit var eregClient: EregClient

        @MockkBean(relaxed = true)
        lateinit var pdfUtils: PdfUtils

        @MockkBean(relaxed = true)
        lateinit var attachmentValidator: AttachmentValidator

        @MockkBean(relaxed = true)
        lateinit var safClient: SafGraphQlClient

        @MockkBean(relaxed = true)
        lateinit var tokenUtil: TokenUtil

        @MockkBean(relaxed = true)
        lateinit var vedtakKafkaProducer: VedtakKafkaProducer

        @MockkBean(relaxed = true)
        lateinit var kabalDocumentGateway: KabalDocumentGateway

    }

    //@Autowired
    //lateinit var entityManager: EntityManager
    //lateinit var entityManager: TestEntityManager

    @Autowired
    lateinit var klagebehandlingRepository: KlagebehandlingRepository

    @Autowired
    lateinit var mottakRepository: MottakRepository

    @SpykBean
    lateinit var klagebehandlingDistribusjonService: KlagebehandlingDistribusjonService

    @MockkBean
    lateinit var dokDistFordelingClient: DokDistFordelingClient

    private val klagebehandlingId = UUID.randomUUID()

    private val vedtakId = UUID.randomUUID()

    private val fnr = "12345678910"

    private val journalpostId = "5678"

    @SpykBean
    lateinit var vedtakDistribusjonService: VedtakDistribusjonService

    @SpykBean
    lateinit var vedtakJournalfoeringService: VedtakJournalfoeringService

    @SpykBean
    lateinit var kafkaVedtakEventRepository: KafkaVedtakEventRepository

    @SpykBean
    lateinit var klagebehandlingAvslutningService: KlagebehandlingAvslutningService

    @MockkBean
    lateinit var fileApiService: FileApiService

    @SpykBean
    lateinit var journalpostGateway: JournalpostGateway

    @SpykBean
    lateinit var vedtakService: VedtakService

    private val mottak = Mottak(
        tema = Tema.OMS,
        type = Type.KLAGE,
        klager = Klager(partId = PartId(type = PartIdType.PERSON, value = fnr)),
        kildeReferanse = UUID.randomUUID().toString(),
        oversendtKaDato = LocalDateTime.now(),
        kildesystem = Fagsystem.K9,
        ytelse = "ABC"
    )

    private val klage = Klagebehandling(
        id = klagebehandlingId,
        klager = Klager(partId = PartId(type = PartIdType.PERSON, value = fnr)),
        sakenGjelder = SakenGjelder(
            partId = PartId(type = PartIdType.PERSON, value = fnr),
            skalMottaKopi = false
        ),
        tema = Tema.OMS,
        type = Type.KLAGE,
        frist = LocalDate.now(),
        hjemler = mutableSetOf(
            Hjemmel.FTL_8_7
        ),
        created = LocalDateTime.now(),
        modified = LocalDateTime.now(),
        mottattKlageinstans = LocalDateTime.now(),
        kildesystem = Fagsystem.K9,
        mottakId = mottak.id,
        vedtak = Vedtak(
            id = vedtakId,
            utfall = Utfall.MEDHOLD,
            mellomlagerId = "123"
        )
    )

    @Test
    fun `save klagebehandling`() {
        mottakRepository.save(mottak)

        klagebehandlingRepository.save(klage)

        klagebehandlingDistribusjonService.distribuerKlagebehandling(klagebehandlingId)

        klagebehandlingRepository.findByIdOrNull(klagebehandlingId) ?: throw NullPointerException()
    }

    @Test
    fun `distribusjon av klagebehandling fører til dokdistReferanse, ferdig distribuert vedtak og avsluttet klagebehandling`() {
        val dokdistResponse = DistribuerJournalpostResponse(UUID.randomUUID())
        val dokarkivResponse = journalpostId
        val fileApiServiceResponse = DokumentInnholdOgTittel(
            "title",
            ByteArray(8),
            MediaType.APPLICATION_PDF
        )

        every { journalpostGateway.createJournalpostAsSystemUser(any(), any(), any()) } returns dokarkivResponse

        every { dokDistFordelingClient.distribuerJournalpost(any()) } returns dokdistResponse

        every { kafkaVedtakEventRepository.save(any()) } returns null

        every { fileApiService.getUploadedDocumentAsSystemUser(any()) } returns fileApiServiceResponse

        every { fileApiService.deleteDocumentAsSystemUser(any()) } returns Unit

        mottakRepository.save(mottak)

        klagebehandlingRepository.save(klage)

        klagebehandlingDistribusjonService.distribuerKlagebehandling(klagebehandlingId)

        val result = klagebehandlingRepository.getOne(klagebehandlingId)
        val resultingVedtak = result.getVedtakOrException()
        val brevMottaker = resultingVedtak.brevmottakere.first()

        assertThat(brevMottaker.dokdistReferanse).isNotNull
        assertThat(brevMottaker.ferdigstiltIJoark).isNotNull
        assertThat(brevMottaker.dokdistReferanse).isNotNull
        assertThat(resultingVedtak.ferdigDistribuert).isNotNull
        assertThat(resultingVedtak.mellomlagerId).isNull()
        assertThat(result.avsluttet).isNotNull
    }
}