package no.nav.klage.oppgave.eventlisteners

import io.mockk.every
import io.mockk.mockk
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Sakstype
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Feilregistrering
import no.nav.klage.oppgave.domain.behandling.embedded.Ferdigstilling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.behandling.embedded.Tildeling
import no.nav.klage.oppgave.domain.behandling.historikk.TildelingHistorikk
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Felt
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.service.StatistikkTilDVHV2Service
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class StatistikkTilDVHV2ServiceTest {
    private val kafkaEventRepository: KafkaEventRepository = mockk()
    private val savedEvents = mutableListOf<KafkaEvent>()

    private val service = StatistikkTilDVHV2Service(kafkaEventRepository = kafkaEventRepository)

    private val saksbehandler = "Z123456"
    private val enhet = "4291"
    private val medunderskriverFelt = Felt.MEDUNDERSKRIVERIDENT

    @BeforeEach
    fun setup() {
        savedEvents.clear()
        every { kafkaEventRepository.save(any()) } answers {
            firstArg<KafkaEvent>().also { savedEvents += it }
        }
    }

    @Test
    fun `OPPRETTET is sent when klagebehandling is created`() {
        val behandling = klagebehandling()

        service.process(event(behandling, Felt.KLAGEBEHANDLING_OPPRETTET))

        val payload = singlePayload()
        assertThat(savedEvents.single().type).isEqualTo(EventType.STATS_DVH_V2)
        assertThat(payload["behandlingStatus"].asString()).isEqualTo("OPPRETTET")
        assertThat(payload["behandlingType"].asString()).isEqualTo("KLAGE")
        assertThat(payload["behandlingIdKabal"].asString()).isEqualTo(behandling.id.toString())
        assertThat(payload["saksbehandler"].isNull).isTrue
        assertThat(payload["ansvarligEnhetKode"].isNull).isTrue
        assertThat(payload["behandlingStartetKA"].isNull).isTrue
        assertThat(payload["overfoertKA"].asString()).isEqualTo(behandling.mottattKlageinstans.toLocalDate().toString())
        assertThat(payload["endringstid"].asString()).startsWith(behandling.created.toLocalDate().toString())
    }

    @Test
    fun `MOTTATT alone is not sent, since OPPRETTET is always published as well`() {
        service.process(event(klagebehandling(), Felt.KLAGEBEHANDLING_MOTTATT))

        assertThat(savedEvents).isEmpty()
    }

    @Test
    fun `changes that are not part of the flow are not sent`() {
        service.process(event(klagebehandling(), medunderskriverFelt))
        service.process(event(klagebehandling(), Felt.UTFALL_ID))

        assertThat(savedEvents).isEmpty()
    }

    @Test
    fun `nothing is sent when gosysOppgaveRequired`() {
        service.process(event(klagebehandling(gosysOppgaveRequired = true), Felt.KLAGEBEHANDLING_OPPRETTET))

        assertThat(savedEvents).isEmpty()
    }

    @Test
    fun `TILDELT_SAKSBEHANDLER has saksbehandler, enhet and first tildeling date`() {
        val first = LocalDateTime.of(2026, 4, 30, 14, 20)
        val behandling =
            klagebehandling().apply {
                tildelingHistorikk += tildelingHistorikk(saksbehandlerident = "Z999999", tidspunkt = first)
                tildeling = Tildeling(saksbehandlerident = saksbehandler, enhet = enhet, tidspunkt = first.plusDays(3))
                modified = first.plusDays(3)
            }

        service.process(event(behandling, Felt.TILDELT_SAKSBEHANDLERIDENT))

        val payload = singlePayload()
        assertThat(payload["behandlingStatus"].asString()).isEqualTo("TILDELT_SAKSBEHANDLER")
        assertThat(payload["saksbehandler"].asString()).isEqualTo(saksbehandler)
        assertThat(payload["saksbehandlerEnhet"].asString()).isEqualTo(enhet)
        assertThat(payload["ansvarligEnhetKode"].asString()).isEqualTo(enhet)
        assertThat(payload["behandlingStartetKA"].asString()).isEqualTo("2026-04-30")
        assertThat(payload["endringstid"].asString()).startsWith("2026-05-03T14:20")
    }

    @Test
    fun `fradeling is sent as TILDELT_SAKSBEHANDLER without saksbehandler, and keeps behandlingStartetKA`() {
        val first = LocalDateTime.of(2026, 4, 30, 14, 20)
        val behandling =
            klagebehandling().apply {
                tildelingHistorikk += tildelingHistorikk(saksbehandlerident = saksbehandler, tidspunkt = first)
                tildelingHistorikk += tildelingHistorikk(saksbehandlerident = null, tidspunkt = first.plusMinutes(10))
                tildeling = null
                modified = first.plusMinutes(10)
            }

        service.process(event(behandling, Felt.TILDELT_SAKSBEHANDLERIDENT))

        val payload = singlePayload()
        assertThat(payload["behandlingStatus"].asString()).isEqualTo("TILDELT_SAKSBEHANDLER")
        assertThat(payload["saksbehandler"].isNull).isTrue
        assertThat(payload["saksbehandlerEnhet"].isNull).isTrue
        assertThat(payload["ansvarligEnhetKode"].isNull).isTrue
        assertThat(payload["behandlingStartetKA"].asString()).isEqualTo("2026-04-30")
        assertThat(payload["endringstid"].asString()).startsWith("2026-04-30T14:30")
    }

    @Test
    fun `AVSLUTTET klagebehandling has resultat, vedtaksdato and enhet`() {
        val avsluttet = LocalDateTime.of(2026, 4, 30, 14, 32)
        val behandling =
            klagebehandling().apply {
                tildeling = Tildeling(saksbehandlerident = saksbehandler, enhet = enhet, tidspunkt = avsluttet.minusDays(1))
                utfall = Utfall.STADFESTELSE
                ferdigstilling = ferdigstilling(avsluttet)
            }

        service.process(event(behandling, Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT))

        val payload = singlePayload()
        assertThat(payload["behandlingStatus"].asString()).isEqualTo("AVSLUTTET")
        assertThat(payload["resultat"].asString()).isEqualTo(Utfall.STADFESTELSE.name)
        assertThat(payload["vedtaksdato"].asString()).isEqualTo("2026-04-30")
        assertThat(payload["ansvarligEnhetKode"].asString()).isEqualTo(enhet)
        assertThat(payload["endringstid"].asString()).startsWith("2026-04-30T14:32")
    }

    @Test
    fun `feilregistrering is sent as AVSLUTTET with resultat Feilregistrert`() {
        val registered = LocalDateTime.of(2026, 5, 1, 10, 0)
        val behandling =
            klagebehandling().apply {
                feilregistrering =
                    Feilregistrering(
                        navIdent = saksbehandler,
                        navn = null,
                        registered = registered,
                        reason = "test",
                        fagsystem = Fagsystem.K9,
                    )
            }

        service.process(event(behandling, Felt.FEILREGISTRERING))

        val payload = singlePayload()
        assertThat(payload["behandlingStatus"].asString()).isEqualTo("AVSLUTTET")
        assertThat(payload["resultat"].asString()).isEqualTo("Feilregistrert")
        assertThat(payload["endringstid"].asString()).startsWith("2026-05-01T10:00")
    }

    @Test
    fun `AVSLUTTET anke i Trygderetten with kjennelse has ansvarligEnhetKode TR0000`() {
        val kjennelse = LocalDateTime.of(2026, 5, 6, 14, 32)
        val behandling =
            ankeITrygderettenbehandling().apply {
                tildeling = Tildeling(saksbehandlerident = saksbehandler, enhet = enhet, tidspunkt = kjennelse.minusDays(1))
                utfall = Utfall.STADFESTELSE
                kjennelseMottatt = kjennelse
                ferdigstilling = ferdigstilling(kjennelse.plusHours(1))
            }

        service.process(event(behandling, Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT))

        val payload = singlePayload()
        assertThat(payload["behandlingType"].asString()).isEqualTo("ANKE_I_TRYGDERETTEN_FOER_2027")
        assertThat(payload["ansvarligEnhetKode"].asString()).isEqualTo("TR0000")
        assertThat(payload["saksbehandlerEnhet"].asString()).isEqualTo(enhet)
        assertThat(payload["endringstid"].asString()).startsWith("2026-05-06T14:32")
    }

    @Test
    fun `anke i Trygderetten avsluttet with ny ankebehandling i KA has no resultat and keeps enhet`() {
        val nyAnkebehandling = LocalDateTime.of(2026, 5, 6, 9, 0)
        val behandling =
            ankeITrygderettenbehandling().apply {
                tildeling = Tildeling(saksbehandlerident = saksbehandler, enhet = enhet, tidspunkt = nyAnkebehandling.minusDays(1))
                nyAnkebehandlingKA = nyAnkebehandling
                ferdigstilling = ferdigstilling(nyAnkebehandling.plusMinutes(1))
            }

        service.process(event(behandling, Felt.NY_ANKEBEHANDLING_KA, Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT))

        val payload = singlePayload()
        assertThat(payload["behandlingStatus"].asString()).isEqualTo("AVSLUTTET")
        assertThat(payload["resultat"].isNull).isTrue
        assertThat(payload["ansvarligEnhetKode"].asString()).isEqualTo(enhet)
        assertThat(payload["endringstid"].asString()).startsWith("2026-05-06T09:01")
    }

    @Test
    fun `anke i Trygderetten opphevet with ny behandling i KA has resultat OPPHEVET and TR0000`() {
        val kjennelse = LocalDateTime.of(2026, 5, 12, 14, 32)
        val behandling =
            ankeITrygderettenbehandling().apply {
                tildeling = Tildeling(saksbehandlerident = saksbehandler, enhet = enhet, tidspunkt = kjennelse.minusDays(1))
                utfall = Utfall.OPPHEVET
                kjennelseMottatt = kjennelse
                nyBehandlingEtterTROpphevet = kjennelse.plusHours(1)
                ferdigstilling = ferdigstilling(kjennelse.plusHours(1))
            }

        service.process(event(behandling, Felt.NY_BEHANDLING_ETTER_TR_OPPHEVET, Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT))

        val payload = singlePayload()
        assertThat(payload["resultat"].asString()).isEqualTo(Utfall.OPPHEVET.name)
        assertThat(payload["ansvarligEnhetKode"].asString()).isEqualTo("TR0000")
    }

    @Test
    fun `OPPRETTET then TILDELT_SAKSBEHANDLER when behandling is created already assigned`() {
        val tildelt = LocalDateTime.of(2026, 5, 1, 8, 0)
        val behandling =
            klagebehandling(type = Type.ANKE_FOER_2027).apply {
                tildeling = Tildeling(saksbehandlerident = saksbehandler, enhet = enhet, tidspunkt = tildelt)
            }

        service.process(event(behandling, Felt.ANKEBEHANDLING_OPPRETTET_BASERT_PAA_ANKE_I_TRYGDERETTEN))

        assertThat(savedEvents).hasSize(2)
        assertThat(savedEvents[0].created).isBefore(savedEvents[1].created)
        val opprettet = savedEvents[0].payload()
        val tildeltPayload = savedEvents[1].payload()
        assertThat(opprettet["behandlingStatus"].asString()).isEqualTo("OPPRETTET")
        assertThat(opprettet["saksbehandler"].isNull).isTrue
        assertThat(opprettet["behandlingStartetKA"].isNull).isTrue
        assertThat(tildeltPayload["behandlingStatus"].asString()).isEqualTo("TILDELT_SAKSBEHANDLER")
        assertThat(tildeltPayload["saksbehandler"].asString()).isEqualTo(saksbehandler)
        assertThat(tildeltPayload["behandlingStartetKA"].asString()).isEqualTo("2026-05-01")
    }

    @Test
    fun `Infotrygd behandlingId is formatted as in v1`() {
        service.process(
            event(
                klagebehandling(fagsystem = Fagsystem.IT01, fagsakId = "1234A56789"),
                Felt.KLAGEBEHANDLING_OPPRETTET,
            ),
        )

        assertThat(singlePayload()["behandlingId"].asString()).isEqualTo("1234-A-56789")
    }

    private fun singlePayload(): JsonNode = savedEvents.single().payload()

    private fun KafkaEvent.payload(): JsonNode = jacksonObjectMapper().readTree(jsonPayload)

    private fun event(
        behandling: Behandling,
        vararg felter: Felt,
    ) = BehandlingChangedEvent(
        behandling = behandling,
        changeList =
            felter.map {
                BehandlingChangedEvent.Change(saksbehandlerident = null, felt = it, behandlingId = behandling.id)
            },
    )

    private fun ferdigstilling(avsluttetAvSaksbehandler: LocalDateTime) =
        Ferdigstilling(
            avsluttetAvSaksbehandler = avsluttetAvSaksbehandler,
            navIdent = saksbehandler,
            navn = "Test",
        )

    private fun tildelingHistorikk(
        saksbehandlerident: String?,
        tidspunkt: LocalDateTime,
    ) = TildelingHistorikk(
        saksbehandlerident = saksbehandlerident,
        enhet = saksbehandlerident?.let { enhet },
        hjemmelIdList = null,
        tidspunkt = tidspunkt,
        fradelingReason = null,
        utfoerendeIdent = null,
        utfoerendeNavn = null,
    )

    private fun klagebehandling(
        type: Type = Type.KLAGE,
        fagsystem: Fagsystem = Fagsystem.K9,
        fagsakId: String = "fagsakId",
        gosysOppgaveRequired: Boolean = false,
    ) = Klagebehandling(
        mottattVedtaksinstans = LocalDate.now(),
        previousSaksbehandlerident = null,
        avsenderEnhetFoersteinstans = "",
        kommentarFraFoersteinstans = null,
        klager = Klager(id = UUID.randomUUID(), partId = PartId(type = PartIdType.VIRKSOMHET, value = "889640782")),
        sakenGjelder = SakenGjelder(id = UUID.randomUUID(), partId = PartId(type = PartIdType.VIRKSOMHET, value = "889640782")),
        prosessfullmektig = null,
        ytelse = Ytelse.OMS_OMP,
        type = type,
        kildeReferanse = "kildeReferanse",
        dvhReferanse = null,
        fagsystem = fagsystem,
        fagsakId = fagsakId,
        sakstype = Sakstype.FAGSAK,
        arkivsaksnummer = null,
        mottattKlageinstans = LocalDateTime.of(2026, 4, 29, 14, 15),
        frist = LocalDate.now(),
        tildeling = null,
        tildelingHistorikk = mutableSetOf(),
        kakaKvalitetsvurderingId = UUID.randomUUID(),
        kakaKvalitetsvurderingVersion = 0,
        saksdokumenter = mutableSetOf(),
        hjemler = setOf(),
        sattPaaVent = null,
        gosysOppgaveId = null,
        varsletBehandlingstid = null,
        forlengetBehandlingstidDraft = null,
        gosysOppgaveRequired = gosysOppgaveRequired,
        initiatingSystem = Behandling.InitiatingSystem.KABAL,
        previousBehandlingId = null,
    )

    private fun ankeITrygderettenbehandling() =
        AnkeITrygderettenbehandlingFoer2027(
            klager = Klager(id = UUID.randomUUID(), partId = PartId(type = PartIdType.VIRKSOMHET, value = "889640782")),
            sakenGjelder = SakenGjelder(id = UUID.randomUUID(), partId = PartId(type = PartIdType.VIRKSOMHET, value = "889640782")),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.ANKE_I_TRYGDERETTEN_FOER_2027,
            kildeReferanse = "kildeReferanse",
            dvhReferanse = null,
            fagsystem = Fagsystem.K9,
            fagsakId = "fagsakId",
            sakstype = Sakstype.FAGSAK,
            arkivsaksnummer = null,
            mottattKlageinstans = LocalDateTime.of(2026, 4, 29, 14, 15),
            frist = LocalDate.now(),
            tildeling = null,
            tildelingHistorikk = mutableSetOf(),
            saksdokumenter = mutableSetOf(),
            hjemler = setOf(),
            sattPaaVent = null,
            sendtTilTrygderetten = LocalDateTime.of(2026, 5, 4, 14, 15),
            kjennelseMottatt = null,
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )
}
