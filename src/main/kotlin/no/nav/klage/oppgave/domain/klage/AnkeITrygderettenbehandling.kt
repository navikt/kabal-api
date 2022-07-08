package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.domain.Behandling
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity

@Entity
@DiscriminatorValue("anke_i_trygderetten")
class AnkeITrygderettenbehandling(
    @Column(name = "sendt_til_trygderetten")
    var sendtTilTrygderetten: LocalDateTime? = null,
    @Column(name = "kjennelse_mottatt")
    var kjennelseMottatt: LocalDateTime? = null,

    //Common properties
    id: UUID = UUID.randomUUID(),
    klager: Klager,
    sakenGjelder: SakenGjelder,
    ytelse: Ytelse,
    type: Type,
    kildeReferanse: String,
    dvhReferanse: String? = null,
    sakFagsystem: Fagsystem,
    sakFagsakId: String? = null,
    //Settes automatisk i klage, må kunne justeres i anke. Bør også representeres i delbehandlinger. Må gjøres entydig i anke, hører antageligvis ikke hjemme i felles klasse.
    mottattKlageinstans: LocalDateTime,
    //TODO: Trenger denne være nullable? Den blir da alltid satt i createKlagebehandlingFromMottak?
    //Litt usikkert om dette hører mest hjemme her eller på delbehandlinger.
    frist: LocalDate? = null,
    //Hører hjemme på delbehandlinger, men her er det mer usikkerhet enn for medunderskriver. Litt om pragmatikken, bør se hva som er enklest å få til.
    tildeling: Tildeling? = null,
    //Hører hjemme på delbehandlinger, men her er det mer usikkerhet enn for medunderskriver
    tildelingHistorikk: MutableSet<TildelingHistorikk> = mutableSetOf(),
    //Hovedbehandling
    kakaKvalitetsvurderingId: UUID? = null,
    created: LocalDateTime = LocalDateTime.now(),
    modified: LocalDateTime = LocalDateTime.now(),
    delbehandlinger: Set<Delbehandling>,
    saksdokumenter: MutableSet<Saksdokument> = mutableSetOf(),
    hjemler: Set<Hjemmel> = emptySet(),
    sattPaaVent: LocalDateTime? = null,
) : Behandling(
    id = id,
    klager = klager,
    sakenGjelder = sakenGjelder,
    ytelse = ytelse,
    type = type,
    kildeReferanse = kildeReferanse,
    mottattKlageinstans = mottattKlageinstans,
    modified = modified,
    created = created,
    kakaKvalitetsvurderingId = kakaKvalitetsvurderingId,
    tildelingHistorikk = tildelingHistorikk,
    tildeling = tildeling,
    frist = frist,
    sakFagsakId = sakFagsakId,
    sakFagsystem = sakFagsystem,
    dvhReferanse = dvhReferanse,
    delbehandlinger = delbehandlinger,
    saksdokumenter = saksdokumenter,
    hjemler = hjemler,
    sattPaaVent = sattPaaVent,
) {
    override fun toString(): String {
        return "Ankebehandling(id=$id, " +
                "modified=$modified, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AnkeITrygderettenbehandling

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    /*
    Mulige utfall av første delbehandling:

    HVIS delbehandling er AVSLUTTET
     OG utfall er en av denne mengden: {STADFESTELSE, AVVIST, ?DELVIS_MEDHOLD?}
     DA skal innstillingsbrev sendes til bruker
       OG status skal settes til PÅ VENT
       OG Ankebehandlingen får en datoverdi for "ventetid påbegynt"
       "Ventetid påbegynt" er utledet av datoverdi for delbehandling AVSLUTTET

    HVIS delbehandling er AVSLUTTET
     OG utfall er en av denne mengden: {TRUKKET, OPPHEVET, MEDHOLD, UGUNST}
     DA skal infobrev sendes til bruker
       OG status er AVSLUTTET
       OG Ankbehandlingen anses som ferdig

    RETUR er ikke aktuelt for anker, skal ikke være et valg for saksbehandler

    SEARCH lager en liste med anker på vent basert på statusen PÅ VENT

    Dette fører til opprettelse av andre delbehandling:
     - Noen trykker på knappen Gjenåpne

     Situasjonen blir at vi har en ankebehandling med en åpen 2. delbehandling

     */
}