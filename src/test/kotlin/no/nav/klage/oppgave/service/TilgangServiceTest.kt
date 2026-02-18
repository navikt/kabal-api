package no.nav.klage.oppgave.service

import io.mockk.every
import io.mockk.mockk
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.*
import no.nav.klage.oppgave.domain.person.Person
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class TilgangServiceTest {
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService = mockk()

    private val saksbehandlerService: SaksbehandlerService = mockk()

    private val klageLookupGateway: KlageLookupGateway = mockk()

    private val tilgangService =
        TilgangService(
            innloggetSaksbehandlerService = innloggetSaksbehandlerService,
            saksbehandlerService = saksbehandlerService,
            klageLookupGateway = klageLookupGateway,
        )

    @Test
    fun `verifySaksbehandlersSkrivetilgang gir feil ved avsluttet`() {
        val klagebehandling = getKlagebehandling()

        klagebehandling.ferdigstilling = Ferdigstilling(
            avsluttet = LocalDateTime.now(),
            avsluttetAvSaksbehandler = LocalDateTime.now(),
            navIdent = "navIdent",
            navn = "navn",
        )

        assertThrows<BehandlingAvsluttetException> {
            tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(
                klagebehandling
            )
        }
    }

    @Test
    fun `verifySaksbehandlersSkrivetilgang gir feil ved avsluttet av saksbehandler`() {
        val klagebehandling = getKlagebehandling()

        klagebehandling.ferdigstilling = Ferdigstilling(
            avsluttetAvSaksbehandler = LocalDateTime.now(),
            navIdent = "navIdent",
            navn = "navn",
        )

        assertThrows<BehandlingAvsluttetException> {
            tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(
                klagebehandling
            )
        }
    }

    @Test
    fun `verifySaksbehandlersSkrivetilgang gir feil ved annen tildelt saksbehandler`() {
        val klagebehandling = getKlagebehandling()
        klagebehandling.tildeling =
            Tildeling(saksbehandlerident = "Z123456", enhet = "", tidspunkt = LocalDateTime.now())

        every { innloggetSaksbehandlerService.getInnloggetIdent() }.returns("Z654321")

        assertThrows<MissingTilgangException> {
            tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(
                klagebehandling
            )
        }
    }

    @Test
    fun `verifySaksbehandlersSkrivetilgang gir feil når ingen har tildelt`() {
        val klagebehandling = getKlagebehandling()

        every { innloggetSaksbehandlerService.getInnloggetIdent() }.returns("Z654321")

        assertThrows<MissingTilgangException> {
            tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(
                klagebehandling
            )
        }
    }

    @Test
    fun `verifySaksbehandlersSkrivetilgang gir ok ved samme ident`() {
        val klagebehandling = getKlagebehandling()
        klagebehandling.tildeling =
            Tildeling(saksbehandlerident = "Z123456", enhet = "", tidspunkt = LocalDateTime.now())

        every { innloggetSaksbehandlerService.getInnloggetIdent() }.returns("Z123456")

        assertThat(tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling)).isEqualTo(Unit)
    }
}


fun getKlagebehandling(): Klagebehandling = Klagebehandling(
    klager = Klager(
        id = UUID.randomUUID(),
        partId = PartId(type = PartIdType.PERSON, value = "23452354")
    ),
    sakenGjelder = SakenGjelder(
        id = UUID.randomUUID(),
        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
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
    avsenderEnhetFoersteinstans = "4100",
    mottattVedtaksinstans = LocalDate.now(),
    kakaKvalitetsvurderingId = UUID.randomUUID(),
    kakaKvalitetsvurderingVersion = 2,
    previousSaksbehandlerident = "C78901",
    gosysOppgaveId = null,
    varsletBehandlingstid = null,
    forlengetBehandlingstidDraft = null,
    gosysOppgaveRequired = false,
    initiatingSystem = Behandling.InitiatingSystem.KABAL,
    previousBehandlingId = null,
)

fun getPerson(): Person = Person(
    foedselsnr = "",
    fornavn = "",
    mellomnavn = "",
    etternavn = "",
    beskyttelsesbehov = null,
    kjoenn = "",
    vergemaalEllerFremtidsfullmakt = false,
    doed = null,
    sikkerhetstiltak = null,
)