package no.nav.klage.oppgave.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Sakstype
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.clients.kaka.model.response.KakaOutput
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandling
import no.nav.klage.oppgave.domain.behandling.Gjenopptaksbehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.behandling.embedded.Tildeling
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.repositories.GjenopptaksbehandlingRepository
import no.nav.klage.oppgave.util.KakaVersionUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class GjenopptaksbehandlingServiceTest {
    private val repository = mockk<GjenopptaksbehandlingRepository>()
    private val dokumentService = mockk<DokumentService>()
    private val behandlingService = mockk<BehandlingService>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val kakaApiGateway = mockk<KakaApiGateway>()
    private val kakaVersionUtil = mockk<KakaVersionUtil>()
    private val kvalitetsvurderingId = UUID.randomUUID()

    private val service =
        GjenopptaksbehandlingService(
            gjenopptaksbehandlingRepository = repository,
            dokumentService = dokumentService,
            behandlingService = behandlingService,
            applicationEventPublisher = eventPublisher,
            kakaApiGateway = kakaApiGateway,
            systembrukerIdent = "SYSTEMBRUKER",
            kakaVersionUtil = kakaVersionUtil,
        )

    @BeforeEach
    fun setUp() {
        every { kakaVersionUtil.getKakaVersion() } returns 2
        every { kakaApiGateway.createKvalitetsvurdering(2) } returns
            KakaOutput(kvalitetsvurderingId = kvalitetsvurderingId, kvalitetsvurderingVersion = 2)
        every { dokumentService.createSaksdokumenterFromJournalpostIdList(any()) } returns mutableSetOf()
        every { repository.save(any<Gjenopptaksbehandling>()) } answers { firstArg() }
    }

    @Test
    fun `journalpost Mottak oppretter konkret gjenopptaksbehandling uten paaanket datooppslag`() {
        val result =
            service.createGjenopptaksbehandlingFromMottak(
                mottak(isBasedOnJournalpost = true),
            )

        assertThat(result).isExactlyInstanceOf(Gjenopptaksbehandling::class.java)
        assertThat((result as Gjenopptaksbehandling).paaanketVedtaksdato).isNull()
        verify(exactly = 0) { behandlingService.resolvePaaanketVedtaksdatoFromPreviousBehandling(any()) }
    }

    @Test
    fun `Kabal Mottak setter paaanket vedtaksdato fra tidligere behandling`() {
        val previousBehandlingId = UUID.randomUUID()
        val paaanketVedtaksdato = LocalDate.of(2025, 3, 4)
        every {
            behandlingService.resolvePaaanketVedtaksdatoFromPreviousBehandling(previousBehandlingId)
        } returns paaanketVedtaksdato

        val result =
            service.createGjenopptaksbehandlingFromMottak(
                mottak(
                    isBasedOnJournalpost = false,
                    forrigeBehandlingId = previousBehandlingId,
                ),
            ) as Gjenopptaksbehandling

        assertThat(result).isExactlyInstanceOf(Gjenopptaksbehandling::class.java)
        assertThat(result.paaanketVedtaksdato).isEqualTo(paaanketVedtaksdato)
        verify(exactly = 1) {
            behandlingService.resolvePaaanketVedtaksdatoFromPreviousBehandling(previousBehandlingId)
        }
    }

    @Test
    fun `ny behandling fra Trygderetten kopierer metadata og lenker til kilden uten aa slaa opp opprinnelig gjenopptak`() {
        val sourceId = UUID.randomUUID()
        val date = LocalDate.of(2024, 9, 17)
        val source =
            GjenopptakITrygderettenbehandling(
                id = sourceId,
                sendtTilTrygderetten = LocalDateTime.of(2024, 1, 1, 0, 0),
                paaanketVedtaksdato = date,
                forsterketRett = true,
                previousBehandlingId = UUID.randomUUID(),
                klager = klager(),
                sakenGjelder = sakenGjelder(),
                prosessfullmektig = null,
                ytelse = Ytelse.OMS_OMP,
                type = Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027,
                kildeReferanse = "source-ref",
                fagsystem = Fagsystem.K9,
                fagsakId = "case-id",
                sakstype = Sakstype.FAGSAK,
                arkivsaksnummer = null,
                mottattKlageinstans = LocalDateTime.of(2024, 1, 1, 0, 0),
                frist = LocalDate.of(2024, 4, 1),
                tildeling =
                    Tildeling(
                        saksbehandlerident = "Z12345",
                        enhet = "4219",
                        tidspunkt = LocalDateTime.of(2024, 1, 2, 0, 0),
                    ),
                previousSaksbehandlerident = "Z12345",
                gosysOppgaveId = null,
                gosysOppgaveRequired = false,
                initiatingSystem = Behandling.InitiatingSystem.KABAL,
            )

        val result =
            service.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(source)

        assertThat(result).isExactlyInstanceOf(Gjenopptaksbehandling::class.java)
        assertThat(result.previousBehandlingId).isEqualTo(sourceId)
        assertThat(result.paaanketVedtaksdato).isEqualTo(date)
        assertThat(result.forsterketRett).isTrue()
        verify(exactly = 0) { repository.findById(any()) }
    }

    private fun mottak(
        isBasedOnJournalpost: Boolean,
        forrigeBehandlingId: UUID? = UUID.randomUUID(),
    ) = Mottak(
        type = Type.BEGJAERING_OM_GJENOPPTAK_FOER_2027,
        klager = klager(),
        prosessfullmektig = null,
        sakenGjelder = sakenGjelder(),
        fagsystem = Fagsystem.K9,
        fagsakId = "case-id",
        sakstype = Sakstype.FAGSAK,
        arkivsaksnummer = null,
        kildeReferanse = "source-ref",
        dvhReferanse = null,
        hjemler = emptySet(),
        forrigeSaksbehandlerident = "Z12345",
        forrigeBehandlendeEnhet = "4219",
        brukersKlageMottattVedtaksinstans = null,
        sakMottattKaDato = LocalDateTime.of(2024, 1, 1, 0, 0),
        frist = LocalDate.of(2024, 4, 1),
        ytelse = Ytelse.OMS_OMP,
        kommentar = null,
        forrigeBehandlingId = forrigeBehandlingId,
        sentFrom = Mottak.Sender.KABIN,
        isBasedOnJournalpost = isBasedOnJournalpost,
        gosysOppgaveRequired = false,
        gosysOppgaveId = null,
        trygderettenSaksnummer = null,
    )

    private fun klager() =
        Klager(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.PERSON, value = "01019012345"),
        )

    private fun sakenGjelder() =
        SakenGjelder(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.PERSON, value = "01019012345"),
        )
}
