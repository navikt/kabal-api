package no.nav.klage.oppgave.service.distribusjon

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import io.mockk.mockk
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.service.DokumentUnderArbeidCommonService
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.clients.arbeidoginntekt.ArbeidOgInntektClient
import no.nav.klage.oppgave.clients.dokdistkanal.DokDistKanalClient
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.kabaldocument.model.response.DokumentEnhetFullfoerOutput
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.saf.SafFacade
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
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


@ActiveProfiles("local")
@Import(BehandlingAvslutningServiceTest.MyTestConfiguration::class)
@SpringBootTest(classes = [KlagebehandlingService::class, BehandlingAvslutningService::class, BehandlingService::class, AnkebehandlingService::class, DokumentUnderArbeidCommonService::class])
@EnableJpaRepositories(basePackages = ["no.nav.klage.oppgave.repositories", "no.nav.klage.dokument.repositories"])
@EntityScan(basePackages = ["no.nav.klage.oppgave.domain", "no.nav.klage.dokument.domain"])
@AutoConfigureDataJpa
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
//@AutoConfigureTestEntityManager
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class BehandlingAvslutningServiceTest {

    companion object {
        @Container
        @JvmField
        val postgreSQLContainer: TestPostgresqlContainer = TestPostgresqlContainer.instance
    }

    @Configuration
    internal class MyTestConfiguration {

        @MockkBean(relaxed = true)
        lateinit var behandlingMapper: BehandlingMapper

        @MockkBean(relaxed = true)
        lateinit var tilgangService: TilgangService

        @MockkBean(relaxed = true)
        lateinit var innloggetSaksbehandlerService: InnloggetSaksbehandlerService

        @MockkBean(relaxed = true)
        lateinit var applicationEventPublisher: ApplicationEventPublisher

        @MockkBean(relaxed = true)
        lateinit var dokumentService: DokumentService

        @MockkBean(relaxed = true)
        lateinit var pdlFacade: PdlFacade

        @MockkBean(relaxed = true)
        lateinit var eregClient: EregClient

        @MockkBean(relaxed = true)
        lateinit var tokenUtil: TokenUtil

        @MockkBean(relaxed = true)
        lateinit var klageFssProxyClient: KlageFssProxyClient

        @MockkBean(relaxed = true)
        lateinit var saksbehandlerService: SaksbehandlerService

        @MockkBean(relaxed = true)
        lateinit var historyService: HistoryService

        @MockkBean(relaxed = true)
        lateinit var kafkaInternalEventService: KafkaInternalEventService

        @MockkBean(relaxed = true)
        lateinit var partSearchService: PartSearchService

        @MockkBean(relaxed = true)
        lateinit var safFacade: SafFacade
    }

    @Autowired
    lateinit var klagebehandlingRepository: KlagebehandlingRepository

    @Autowired
    lateinit var mottakRepository: MottakRepository

    private val klagebehandlingId = UUID.randomUUID()

    private val vedtakId = UUID.randomUUID()

    private val fnr = "12345678910"
    private val journalpostId = "5678"

    @MockkBean(relaxed = true)
    lateinit var kafkaEventRepository: KafkaEventRepository

    @MockkBean(relaxed = true)
    lateinit var dokumentUnderArbeidRepository: DokumentUnderArbeidRepository

    @MockkBean
    lateinit var ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService

    @MockkBean
    lateinit var behandlingEtterTrygderettenOpphevetService: BehandlingEtterTrygderettenOpphevetService

    @MockkBean
    lateinit var arbeidOgInntektClient: ArbeidOgInntektClient

    @SpykBean
    lateinit var behandlingAvslutningService: BehandlingAvslutningService

    @MockkBean
    lateinit var dokDistKanalClient: DokDistKanalClient

    @MockkBean(relaxed = true)
    lateinit var kabalDocumentGateway: KabalDocumentGateway

    @MockkBean(relaxed = true)
    lateinit var kabalInnstillingerService: KabalInnstillingerService

    @MockkBean(relaxed = true)
    lateinit var kakaApiGateway: KakaApiGateway

    @MockkBean(relaxed = true)
    lateinit var gosysOppgaveService: GosysOppgaveService


    @MockkBean(relaxed = true)
    lateinit var kodeverkService: KodeverkService

    private val mottak = Mottak(
        ytelse = Ytelse.OMS_OMP,
        type = Type.KLAGE,
        klager = Klager(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.PERSON, value = fnr)
        ),
        kildeReferanse = UUID.randomUUID().toString(),
        sakMottattKaDato = LocalDateTime.now(),
        fagsystem = Fagsystem.K9,
        fagsakId = "123",
        forrigeBehandlendeEnhet = "0101",
        brukersKlageMottattVedtaksinstans = LocalDate.now(),
        kommentar = null,
        hjemler = emptySet(),
        prosessfullmektig = null,
        sakenGjelder = null,
        dvhReferanse = null,
        forrigeSaksbehandlerident = null,
        frist = null,
        forrigeBehandlingId = null,
        sentFrom = Mottak.Sender.FAGSYSTEM,
    )

    private val klage = Klagebehandling(
        id = klagebehandlingId,
        klager = Klager(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.PERSON, value = fnr)
        ),
        sakenGjelder = SakenGjelder(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.PERSON, value = fnr),
        ),
        prosessfullmektig = null,
        ytelse = Ytelse.OMS_OMP,
        type = Type.KLAGE,
        frist = LocalDate.now(),
        hjemler = mutableSetOf(
            Hjemmel.FTRL_8_7
        ),
        created = LocalDateTime.now(),
        modified = LocalDateTime.now(),
        mottattKlageinstans = LocalDateTime.now(),
        fagsystem = Fagsystem.K9,
        fagsakId = "123",
        kildeReferanse = "abc",
        mottakId = mottak.id,
        avsenderEnhetFoersteinstans = "0101",
        mottattVedtaksinstans = LocalDate.now(),
        kakaKvalitetsvurderingId = UUID.randomUUID(),
        kakaKvalitetsvurderingVersion = 2,
        utfall = Utfall.MEDHOLD,
        extraUtfallSet = setOf(Utfall.MEDHOLD),
        previousSaksbehandlerident = "C78901",
        gosysOppgaveId = null,
        varsletBehandlingstid = null,
        forlengetBehandlingstidDraft = null,
    )

    @Test
    fun `distribusjon av klagebehandling fører til avsluttet klagebehandling`() {

        every { kafkaEventRepository.save(any()) } returns mockk()
        every { kabalDocumentGateway.fullfoerDokumentEnhet(any()) } returns DokumentEnhetFullfoerOutput(
            sourceReferenceWithJoarkReferencesList = listOf(),
        )

        mottakRepository.save(mottak)

        klagebehandlingRepository.save(klage.apply {
            this.ferdigstilling = Ferdigstilling(
                avsluttetAvSaksbehandler = LocalDateTime.now(),
                navIdent = "navIdent",
                navn = "navn",
            )
        })

        behandlingAvslutningService.avsluttBehandling(klagebehandlingId)

        val result = klagebehandlingRepository.findById(klagebehandlingId).get()
        assertThat(result.ferdigstilling?.avsluttet).isNotNull
    }
}