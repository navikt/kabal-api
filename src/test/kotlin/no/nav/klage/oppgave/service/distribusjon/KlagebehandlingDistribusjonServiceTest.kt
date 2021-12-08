package no.nav.klage.oppgave.service.distribusjon

import brave.Tracer
import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import io.mockk.mockk
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import no.nav.klage.oppgave.repositories.MottakRepository
import no.nav.klage.oppgave.service.*
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
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


@ActiveProfiles("local")
@Import(KlagebehandlingDistribusjonServiceTest.MyTestConfiguration::class)
@SpringBootTest(classes = [KlagebehandlingService::class, KlagebehandlingDistribusjonService::class, VedtakService::class])
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
        lateinit var tracer: Tracer

        @MockkBean(relaxed = true)
        lateinit var pdlFacade: PdlFacade

        @MockkBean(relaxed = true)
        lateinit var eregClient: EregClient

        @MockkBean(relaxed = true)
        lateinit var safClient: SafGraphQlClient

        @MockkBean(relaxed = true)
        lateinit var tokenUtil: TokenUtil

        @MockkBean(relaxed = true)
        lateinit var vedtakKafkaProducer: VedtakKafkaProducer

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

    private val klagebehandlingId = UUID.randomUUID()

    private val vedtakId = UUID.randomUUID()

    private val fnr = "12345678910"
    private val journalpostId = "5678"

    @SpykBean
    lateinit var kafkaEventRepository: KafkaEventRepository

    @SpykBean
    lateinit var klagebehandlingAvslutningService: KlagebehandlingAvslutningService

    @SpykBean
    lateinit var vedtakService: VedtakService

    @MockkBean(relaxed = true)
    lateinit var kabalDocumentGateway: KabalDocumentGateway

    @MockkBean(relaxed = true)
    lateinit var kakaApiGateway: KakaApiGateway

    private val mottak = Mottak(
        ytelse = Ytelse.OMS_OMP,
        type = Type.KLAGE,
        klager = Klager(partId = PartId(type = PartIdType.PERSON, value = fnr)),
        kildeReferanse = UUID.randomUUID().toString(),
        oversendtKaDato = LocalDateTime.now(),
        kildesystem = Fagsystem.K9,
        avsenderEnhet = "0101",
        mottattNavDato = LocalDate.now()
    )

    private val klage = Klagebehandling(
        id = klagebehandlingId,
        klager = Klager(partId = PartId(type = PartIdType.PERSON, value = fnr)),
        sakenGjelder = SakenGjelder(
            partId = PartId(type = PartIdType.PERSON, value = fnr),
            skalMottaKopi = false
        ),
        ytelse = Ytelse.OMS_OMP,
        type = Type.KLAGE,
        frist = LocalDate.now(),
        hjemler = mutableSetOf(
            Hjemmel.FTL_8_7
        ),
        created = LocalDateTime.now(),
        modified = LocalDateTime.now(),
        mottattKlageinstans = LocalDateTime.now(),
        kildesystem = Fagsystem.K9,
        kildeReferanse = "abc",
        mottakId = mottak.id,
        vedtak = Vedtak(
            id = vedtakId,
            utfall = Utfall.MEDHOLD,
            dokumentEnhetId = UUID.randomUUID()
        ),
        avsenderEnhetFoersteinstans = "0101",
        mottattFoersteinstans = LocalDate.now()
    )

    @Test
    fun `save klagebehandling`() {
        mottakRepository.save(mottak)

        klagebehandlingRepository.save(klage)

        klagebehandlingDistribusjonService.distribuerKlagebehandling(klagebehandlingId)

        klagebehandlingRepository.findByIdOrNull(klagebehandlingId) ?: throw NullPointerException()
    }

    @Test
    fun `distribusjon av klagebehandling fører til avsluttet klagebehandling`() {

        every { kafkaEventRepository.save(any()) } returns mockk()
        every { kabalDocumentGateway.fullfoerDokumentEnhet(any()) } returns Unit
        every { kabalDocumentGateway.getJournalpostIdForHovedadressat(any()) } returns journalpostId

        mottakRepository.save(mottak)

        klagebehandlingRepository.save(klage)

        klagebehandlingDistribusjonService.distribuerKlagebehandling(klagebehandlingId)

        val result = klagebehandlingRepository.getOne(klagebehandlingId)
        assertThat(result.avsluttet).isNotNull
    }
}