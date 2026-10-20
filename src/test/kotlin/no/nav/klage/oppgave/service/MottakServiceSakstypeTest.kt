package no.nav.klage.oppgave.service

import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Sakstype
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.ytelseToHjemler
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.view.kabin.CreateBehandlingBasedOnKabinInputWithPreviousKabalBehandling
import no.nav.klage.oppgave.api.view.kabin.CreateKlageBasedOnKabinInput
import no.nav.klage.oppgave.api.view.kabin.OversendtPartId
import no.nav.klage.oppgave.api.view.kabin.OversendtPartIdType
import no.nav.klage.oppgave.api.view.kabin.SvarbrevInput
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Ferdigstilling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.behandling.embedded.Tildeling
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.exceptions.OversendtKlageNotValidException
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.env.Environment
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class MottakServiceSakstypeTest {
    private val behandlingRepository = mockk<BehandlingRepository>(relaxed = true)
    private val createBehandlingFromMottak = mockk<CreateBehandlingFromMottak>()
    private val eregClient = mockk<EregClient>()
    private val mottakSlot = slot<Mottak>()

    private lateinit var mottakService: MottakService

    private val ytelse = Ytelse.OMS_OMP
    private val hjemmel: Hjemmel = ytelseToHjemler[ytelse]!!.first { !it.utfases }.hjemmel

    // Organisasjonsnummer til Arbeids- og velferdsdirektoratet (offentlig, gyldig mod11). Unngår fnr i tester.
    private val orgnr = "889640782"

    @BeforeEach
    fun setup() {
        val environment = mockk<Environment>()
        every { environment.activeProfiles } returns emptyArray()

        every { eregClient.isOrganisasjonActive(any()) } returns true
        every { createBehandlingFromMottak.createBehandling(mottak = capture(mottakSlot)) } returns mockk(relaxed = true)

        mottakService =
            MottakService(
                environment = environment,
                klagebehandlingRepository = mockk<KlagebehandlingRepository>(relaxed = true),
                behandlingRepository = behandlingRepository,
                dokumentService = mockk<DokumentService>(relaxed = true),
                norg2Client = mockk<Norg2Client>(relaxed = true),
                meterRegistry = mockk<MeterRegistry>(relaxed = true),
                createBehandlingFromMottak = createBehandlingFromMottak,
                personService = mockk<PersonService>(relaxed = true),
                eregClient = eregClient,
                klageLookupGateway = mockk<KlageLookupGateway>(relaxed = true),
            )
    }

    @Test
    fun `klage from kabin on generell sak without fagsakId is created`() {
        mottakService.createKlageMottakFromKabinInput(
            klageInput(fagsakId = null, sakstypeId = Sakstype.GENERELL_SAK.id, arkivsaksnummer = "140411735"),
        )

        val mottak = mottakSlot.captured
        assertThat(mottak.sakstype).isEqualTo(Sakstype.GENERELL_SAK)
        assertThat(mottak.fagsakId).isNull()
        assertThat(mottak.arkivsaksnummer).isEqualTo("140411735")
        assertThat(mottak.fagsystem).isEqualTo(Fagsystem.FS22)
    }

    @Test
    fun `klage from kabin without sakstype defaults to FAGSAK`() {
        mottakService.createKlageMottakFromKabinInput(klageInput(fagsakId = "123", sakstypeId = null, arkivsaksnummer = null))

        assertThat(mottakSlot.captured.sakstype).isEqualTo(Sakstype.FAGSAK)
        assertThat(mottakSlot.captured.fagsakId).isEqualTo("123")
    }

    @Test
    fun `klage from kabin on fagsak without fagsakId is rejected`() {
        assertThrows<OversendtKlageNotValidException> {
            mottakService.createKlageMottakFromKabinInput(klageInput(fagsakId = null, sakstypeId = null, arkivsaksnummer = null))
        }
        assertThrows<OversendtKlageNotValidException> {
            mottakService.createKlageMottakFromKabinInput(
                klageInput(fagsakId = " ", sakstypeId = Sakstype.FAGSAK.id, arkivsaksnummer = null),
            )
        }
        verify(exactly = 0) { createBehandlingFromMottak.createBehandling(any()) }
    }

    @Test
    fun `klage from kabin on generell sak with fagsakId is rejected`() {
        assertThrows<OversendtKlageNotValidException> {
            mottakService.createKlageMottakFromKabinInput(
                klageInput(fagsakId = "123", sakstypeId = Sakstype.GENERELL_SAK.id, arkivsaksnummer = "140411735"),
            )
        }
        assertThrows<OversendtKlageNotValidException> {
            mottakService.createKlageMottakFromKabinInput(
                klageInput(fagsakId = "", sakstypeId = Sakstype.GENERELL_SAK.id, arkivsaksnummer = "140411735"),
            )
        }
        verify(exactly = 0) { createBehandlingFromMottak.createBehandling(any()) }
    }

    @Test
    fun `klage from kabin on arkivsak or unknown sakstype is rejected`() {
        assertThrows<OversendtKlageNotValidException> {
            mottakService.createKlageMottakFromKabinInput(
                klageInput(fagsakId = "123", sakstypeId = Sakstype.ARKIVSAK.id, arkivsaksnummer = null),
            )
        }
        assertThrows<OversendtKlageNotValidException> {
            mottakService.createKlageMottakFromKabinInput(klageInput(fagsakId = "123", sakstypeId = "99", arkivsaksnummer = null))
        }
        verify(exactly = 0) { createBehandlingFromMottak.createBehandling(any()) }
    }

    @Test
    fun `behandling based on previous kabal behandling inherits sakstype and arkivsaksnummer`() {
        val sourceBehandling = finishedKlagebehandlingOnGenerellSak()
        every { behandlingRepository.findById(sourceBehandling.id) } returns Optional.of(sourceBehandling)

        mottakService.createMottakAndBehandlingFromKabinInputWithPreviousKabalBehandling(
            CreateBehandlingBasedOnKabinInputWithPreviousKabalBehandling(
                typeId = Type.ANKE_FOER_2027.id,
                sourceBehandlingId = sourceBehandling.id,
                mottattNav = LocalDate.now(),
                frist = LocalDate.now().plusWeeks(12),
                klager = null,
                fullmektig = null,
                receivedDocumentJournalpostId = "123456",
                uploadedDocument = null,
                saksbehandlerIdent = null,
                svarbrevInput = svarbrevInput(),
                hjemmelIdList = listOf(hjemmel.id),
                gosysOppgaveId = null,
                trygderettenSaksnummer = null,
            ),
        )

        val mottak = mottakSlot.captured
        assertThat(mottak.sakstype).isEqualTo(Sakstype.GENERELL_SAK)
        assertThat(mottak.fagsakId).isNull()
        assertThat(mottak.arkivsaksnummer).isEqualTo("140411735")
        assertThat(mottak.fagsystem).isEqualTo(Fagsystem.FS22)
    }

    @Test
    fun `anke i trygderetten input inherits sakstype and arkivsaksnummer`() {
        val ankebehandling = finishedAnkebehandlingOnGenerellSak()

        val input = ankebehandling.createAnkeITrygderettenbehandlingFoer2027Input()

        assertThat(input.sakstype).isEqualTo(Sakstype.GENERELL_SAK)
        assertThat(input.fagsakId).isNull()
        assertThat(input.arkivsaksnummer).isEqualTo("140411735")
    }

    private fun klageInput(
        fagsakId: String?,
        sakstypeId: String?,
        arkivsaksnummer: String?,
    ) = CreateKlageBasedOnKabinInput(
        sakenGjelder = OversendtPartId(type = OversendtPartIdType.VIRKSOMHET, value = orgnr),
        klager = null,
        fullmektig = null,
        fagsakId = fagsakId,
        fagsystemId = Fagsystem.FS22.id,
        sakstypeId = sakstypeId,
        arkivsaksnummer = arkivsaksnummer,
        hjemmelIdList = listOf(hjemmel.id),
        forrigeBehandlendeEnhet = "4295",
        klageJournalpostId = "123456",
        brukersHenvendelseMottattNav = LocalDate.now(),
        sakMottattKa = LocalDate.now(),
        frist = LocalDate.now().plusWeeks(12),
        ytelseId = ytelse.id,
        kildereferanse = "kildereferanse",
        saksbehandlerIdent = null,
        svarbrevInput = svarbrevInput(),
        gosysOppgaveId = 1L,
    )

    private fun svarbrevInput() =
        SvarbrevInput(
            title = "title",
            receivers = emptyList(),
            fullmektigFritekst = null,
            initialCustomText = null,
            customText = null,
            varsletBehandlingstidUnits = 12,
            varsletBehandlingstidUnitTypeId = null,
            varsletBehandlingstidUnitType = null,
            doNotSendLetter = true,
            reasonNoLetter = "test",
        )

    private fun sakenGjelder() =
        SakenGjelder(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.VIRKSOMHET, value = orgnr),
        )

    private fun finishedKlagebehandlingOnGenerellSak(): Klagebehandling {
        val sakenGjelder = sakenGjelder()
        return Klagebehandling(
            klager = Klager(id = sakenGjelder.id, partId = sakenGjelder.partId),
            sakenGjelder = sakenGjelder,
            prosessfullmektig = null,
            ytelse = ytelse,
            type = Type.KLAGE,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(hjemmel),
            mottattKlageinstans = LocalDateTime.now(),
            fagsystem = Fagsystem.FS22,
            fagsakId = null,
            sakstype = Sakstype.GENERELL_SAK,
            arkivsaksnummer = "140411735",
            kildeReferanse = "abc",
            avsenderEnhetFoersteinstans = "0101",
            mottattVedtaksinstans = LocalDate.now(),
            kakaKvalitetsvurderingVersion = 2,
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        ).apply {
            tildeling = Tildeling(saksbehandlerident = "Z123456", enhet = "4295", tidspunkt = LocalDateTime.now())
            ferdigstilling = Ferdigstilling(avsluttetAvSaksbehandler = LocalDateTime.now(), navIdent = "Z123456", navn = "navn")
        }
    }

    private fun finishedAnkebehandlingOnGenerellSak(): AnkebehandlingFoer2027 {
        val sakenGjelder = sakenGjelder()
        return AnkebehandlingFoer2027(
            klager = Klager(id = sakenGjelder.id, partId = sakenGjelder.partId),
            sakenGjelder = sakenGjelder,
            prosessfullmektig = null,
            ytelse = ytelse,
            type = Type.ANKE_FOER_2027,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(hjemmel),
            mottattKlageinstans = LocalDateTime.now(),
            fagsystem = Fagsystem.FS22,
            fagsakId = null,
            sakstype = Sakstype.GENERELL_SAK,
            arkivsaksnummer = "140411735",
            kildeReferanse = "abc",
            klageBehandlendeEnhet = "4295",
            kakaKvalitetsvurderingVersion = 2,
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            paaanketVedtaksdato = LocalDate.of(2020, 1, 1),
            forsterketRett = false,
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        ).apply {
            tildeling = Tildeling(saksbehandlerident = "Z123456", enhet = "4295", tidspunkt = LocalDateTime.now())
            ferdigstilling = Ferdigstilling(avsluttetAvSaksbehandler = LocalDateTime.now(), navIdent = "Z123456", navn = "navn")
            utfall = Utfall.INNSTILLING_STADFESTELSE
        }
    }
}
