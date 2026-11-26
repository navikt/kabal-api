package no.nav.klage.oppgave.api.mapper

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Sakstype
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.clients.krrproxy.KrrProxyClient
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingITrygderetten
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.MedunderskriverTildeling
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.person.Person
import no.nav.klage.oppgave.domain.representasjon.Representasjonsforhold
import no.nav.klage.oppgave.repositories.PersonProtectionRepository
import no.nav.klage.oppgave.repositories.SakPersongalleriRepository
import no.nav.klage.oppgave.service.DokDistKanalService
import no.nav.klage.oppgave.service.GosysOppgaveService
import no.nav.klage.oppgave.service.KodeverkService
import no.nav.klage.oppgave.service.PersonService
import no.nav.klage.oppgave.service.SaksbehandlerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@ActiveProfiles("local")
@SpringBootTest(classes = [BehandlingMapper::class])
class BehandlingMapperTest {
    @MockkBean
    lateinit var personService: PersonService

    @MockkBean
    lateinit var norg2Client: Norg2Client

    @MockkBean
    lateinit var eregClient: EregClient

    @MockkBean
    lateinit var saksbehandlerService: SaksbehandlerService

    @MockkBean
    lateinit var kabalDocumentGateway: KabalDocumentGateway

    @MockkBean
    lateinit var krrProxyClient: KrrProxyClient

    @MockkBean
    lateinit var kodeverkService: KodeverkService

    @MockkBean
    lateinit var klageLookupGateway: KlageLookupGateway

    @MockkBean
    lateinit var dokDistKanalService: DokDistKanalService

    @MockkBean
    lateinit var gosysOppgaveService: GosysOppgaveService

    @MockkBean
    lateinit var sakPersongalleriRepository: SakPersongalleriRepository

    @MockkBean
    lateinit var personProtectionRepository: PersonProtectionRepository

    @Autowired
    lateinit var behandlingMapper: BehandlingMapper

    private val fnr = "FNR"
    private val medunderskriverIdent = "MEDUNDERSKRIVER_IDENT"
    private val medunderskriverNavn = "MEDUNDERSKRIVER_NAVN"

    @Test
    fun `mapToMedunderskriverWrapped og mapToMedunderskriverFlowStateView gir forventet resultat når medunderskriver og medunderskriverFlowState ikke er satt`() {
        val klagebehandling = getKlagebehandling()
        val viewResult = behandlingMapper.mapToMedunderskriverWrapped(klagebehandling)
        val flytViewResult = behandlingMapper.mapToMedunderskriverFlowStateView(klagebehandling)

        assertThat(viewResult.employee).isNull()
        assertThat(flytViewResult.flowState).isEqualTo(FlowState.NOT_SENT)
    }

    @Test
    fun `mapToMedunderskriverWrapped og mapToMedunderskriverFlowStateView gir forventet resultat når medunderskriver og medunderskriverFlowState er satt`() {
        val klagebehandling = getKlagebehandlingWithMedunderskriver()
        every { saksbehandlerService.getNameForIdentDefaultIfNull(any()) } returns medunderskriverNavn

        val viewResult = behandlingMapper.mapToMedunderskriverWrapped(klagebehandling)
        val flytViewResult = behandlingMapper.mapToMedunderskriverFlowStateView(klagebehandling)

        assertThat(viewResult.employee?.navIdent).isEqualTo(medunderskriverIdent)
        assertThat(flytViewResult.flowState).isEqualTo(FlowState.SENT)
    }

    @Test
    fun `mapBehandlingITrygderettenbehandlingToBehandlingDetaljerView maps optional saksnummer for pre-2027 appeal`() {
        assertTrygderettenSaksnummerIsMapped(
            behandling = createAnkeITrygderettenbehandlingFoer2027("2026123"),
            expectedSaksnummer = "2026123",
        )
        assertTrygderettenSaksnummerIsMapped(behandling = createAnkeITrygderettenbehandlingFoer2027(null), expectedSaksnummer = null)
    }

    @Test
    fun `mapBehandlingITrygderettenbehandlingToBehandlingDetaljerView maps optional saksnummer for pre-2027 reopening`() {
        assertTrygderettenSaksnummerIsMapped(
            behandling = createGjenopptakITrygderettenbehandlingFoer2027("2026124"),
            expectedSaksnummer = "2026124",
        )
        assertTrygderettenSaksnummerIsMapped(behandling = createGjenopptakITrygderettenbehandlingFoer2027(null), expectedSaksnummer = null)
    }

    @Test
    fun `mapBehandlingITrygderettenbehandlingToBehandlingDetaljerView retains mandatory post-2027 saksnummer`() {
        assertTrygderettenSaksnummerIsMapped(
            behandling = createAnkeITrygderettenbehandlingEtter2027(),
            expectedSaksnummer = "2027123",
        )
    }

    private fun assertTrygderettenSaksnummerIsMapped(
        behandling: BehandlingITrygderetten,
        expectedSaksnummer: String?,
    ) {
        every { personService.getPerson(fnr) } returns testPerson
        every { krrProxyClient.getDigitalKontaktinformasjonForFnrOnBehalfOf(fnr) } returns null
        every { klageLookupGateway.getAddressForPerson(fnr) } returns null
        every { klageLookupGateway.getRepresentasjonsforhold(fnr) } returns
            Representasjonsforhold(fullmakt = emptyList(), vergemaal = emptyList())

        val view =
            behandlingMapper.mapBehandlingITrygderettenbehandlingToBehandlingDetaljerView(
                behandling = behandling,
                person = testPerson,
            )

        assertThat(view.trygderettenSaksnummer).isEqualTo(expectedSaksnummer)
    }

    private val testPerson =
        Person(
            foedselsnr = fnr,
            fornavn = "Test",
            mellomnavn = null,
            etternavn = "Person",
            sammensattNavn = "Test Person",
            kjoenn = null,
            doed = null,
            strengtFortrolig = false,
            strengtFortroligUtland = false,
            fortrolig = false,
            egenAnsatt = false,
            vergemaalEllerFremtidsfullmakt = false,
            sikkerhetstiltak = null,
            falskId = false,
        )

    private fun createAnkeITrygderettenbehandlingFoer2027(trygderettenSaksnummer: String?) =
        AnkeITrygderettenbehandlingFoer2027(
            sendtTilTrygderetten = LocalDateTime.of(2026, 1, 1, 0, 0),
            trygderettenSaksnummer = trygderettenSaksnummer,
            klager = Klager(id = UUID.randomUUID(), partId = PartId(type = PartIdType.PERSON, value = fnr)),
            sakenGjelder =
                SakenGjelder(id = UUID.randomUUID(), partId = PartId(type = PartIdType.PERSON, value = fnr)),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.ANKE_I_TRYGDERETTEN_FOER_2027,
            kildeReferanse = "test-reference",
            fagsystem = Fagsystem.AO01,
            fagsakId = null,
            sakstype = Sakstype.GENERELL_SAK,
            arkivsaksnummer = "TEST-123",
            mottattKlageinstans = LocalDateTime.of(2026, 1, 1, 0, 0),
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

    private fun createGjenopptakITrygderettenbehandlingFoer2027(trygderettenSaksnummer: String?) =
        GjenopptakITrygderettenbehandlingFoer2027(
            sendtTilTrygderetten = LocalDateTime.of(2026, 1, 1, 0, 0),
            trygderettenSaksnummer = trygderettenSaksnummer,
            klager = Klager(id = UUID.randomUUID(), partId = PartId(type = PartIdType.PERSON, value = fnr)),
            sakenGjelder =
                SakenGjelder(id = UUID.randomUUID(), partId = PartId(type = PartIdType.PERSON, value = fnr)),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027,
            kildeReferanse = "test-reference",
            fagsystem = Fagsystem.AO01,
            fagsakId = null,
            sakstype = Sakstype.GENERELL_SAK,
            arkivsaksnummer = "TEST-123",
            mottattKlageinstans = LocalDateTime.of(2026, 1, 1, 0, 0),
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

    private fun createAnkeITrygderettenbehandlingEtter2027() =
        AnkeITrygderettenbehandlingEtter2027(
            sendtTilTrygderetten = LocalDateTime.of(2027, 1, 1, 0, 0),
            trygderettenSaksnummer = "2027123",
            klager = Klager(id = UUID.randomUUID(), partId = PartId(type = PartIdType.PERSON, value = fnr)),
            sakenGjelder =
                SakenGjelder(id = UUID.randomUUID(), partId = PartId(type = PartIdType.PERSON, value = fnr)),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.ANKE_I_TRYGDERETTEN_ETTER_2027,
            kildeReferanse = "test-reference",
            fagsystem = Fagsystem.AO01,
            fagsakId = null,
            sakstype = Sakstype.GENERELL_SAK,
            arkivsaksnummer = "TEST-123",
            mottattKlageinstans = LocalDateTime.of(2027, 1, 1, 0, 0),
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

    private fun getKlagebehandling(): Klagebehandling =
        Klagebehandling(
            fagsystem = Fagsystem.AO01,
            fagsakId = "123",
            sakstype = Sakstype.FAGSAK,
            arkivsaksnummer = null,
            kildeReferanse = "abc",
            klager =
                Klager(
                    id = UUID.randomUUID(),
                    partId = PartId(type = PartIdType.PERSON, value = fnr),
                ),
            sakenGjelder =
                SakenGjelder(
                    id = UUID.randomUUID(),
                    partId = PartId(type = PartIdType.PERSON, value = fnr),
                ),
            prosessfullmektig = null,
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            avsenderEnhetFoersteinstans = "4100",
            mottattVedtaksinstans = LocalDate.now(),
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 2,
            frist = LocalDate.now().plusWeeks(12),
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

    private fun getKlagebehandlingWithMedunderskriver(): Klagebehandling =
        getKlagebehandling().apply {
            medunderskriver =
                MedunderskriverTildeling(
                    saksbehandlerident = medunderskriverIdent,
                    tidspunkt = LocalDateTime.now(),
                )
            medunderskriverFlowState = FlowState.SENT
        }
}
