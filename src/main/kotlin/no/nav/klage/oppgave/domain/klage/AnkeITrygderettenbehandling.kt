package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Entity
@DiscriminatorValue("anke_i_trygderetten")
class AnkeITrygderettenbehandling(
    @Column(name = "sendt_til_trygderetten")
    var sendtTilTrygderetten: LocalDateTime,
    @Column(name = "kjennelse_mottatt")
    var kjennelseMottatt: LocalDateTime? = null,
    /** Tatt over av KA mens den er i TR */
    @Column(name = "ny_behandling_ka")
    var nyBehandlingKA: LocalDateTime? = null,

    //Common properties
    id: UUID = UUID.randomUUID(),
    klager: Klager,
    sakenGjelder: SakenGjelder,
    ytelse: Ytelse,
    type: Type,
    kildeReferanse: String,
    dvhReferanse: String? = null,
    fagsystem: Fagsystem,
    fagsakId: String,
    mottattKlageinstans: LocalDateTime,
    //TODO: Trenger denne være nullable? Den blir da alltid satt i createKlagebehandlingFromMottak?
    frist: LocalDate? = null,
    tildeling: Tildeling? = null,
    created: LocalDateTime = LocalDateTime.now(),
    modified: LocalDateTime = LocalDateTime.now(),
    saksdokumenter: MutableSet<Saksdokument> = mutableSetOf(),
    hjemler: Set<Hjemmel> = emptySet(),
    sattPaaVent: SattPaaVent? = null,
    feilregistrering: Feilregistrering? = null,
    utfall: Utfall? = null,
    extraUtfallSet: Set<Utfall> = setOf(),
    registreringshjemler: MutableSet<Registreringshjemmel> = mutableSetOf(),
    medunderskriver: MedunderskriverTildeling? = null,
    medunderskriverFlowState: FlowState = FlowState.NOT_SENT,
    ferdigstilling: Ferdigstilling? = null,
    rolIdent: String? = null,
    rolFlowState: FlowState = FlowState.NOT_SENT,
    rolReturnedDate: LocalDateTime? = null,
    tildelingHistorikk: MutableSet<TildelingHistorikk> = mutableSetOf(),
    medunderskriverHistorikk: MutableSet<MedunderskriverHistorikk> = mutableSetOf(),
    rolHistorikk: MutableSet<RolHistorikk> = mutableSetOf(),
    klagerHistorikk: MutableSet<KlagerHistorikk> = mutableSetOf(),
    fullmektigHistorikk: MutableSet<FullmektigHistorikk> = mutableSetOf(),
    sattPaaVentHistorikk: MutableSet<SattPaaVentHistorikk> = mutableSetOf(),
    previousSaksbehandlerident: String?,
    oppgaveId: Long?,
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
    tildeling = tildeling,
    frist = frist,
    fagsakId = fagsakId,
    fagsystem = fagsystem,
    dvhReferanse = dvhReferanse,
    saksdokumenter = saksdokumenter,
    hjemler = hjemler,
    sattPaaVent = sattPaaVent,
    feilregistrering = feilregistrering,
    utfall = utfall,
    extraUtfallSet = extraUtfallSet,
    registreringshjemler = registreringshjemler,
    medunderskriver = medunderskriver,
    medunderskriverFlowState = medunderskriverFlowState,
    ferdigstilling = ferdigstilling,
    rolIdent = rolIdent,
    rolFlowState = rolFlowState,
    rolReturnedDate = rolReturnedDate,
    tildelingHistorikk = tildelingHistorikk,
    medunderskriverHistorikk = medunderskriverHistorikk,
    rolHistorikk = rolHistorikk,
    klagerHistorikk = klagerHistorikk,
    fullmektigHistorikk = fullmektigHistorikk,
    sattPaaVentHistorikk = sattPaaVentHistorikk,
    previousSaksbehandlerident = previousSaksbehandlerident,
    oppgaveId = oppgaveId,
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

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    /*
    Mulige utfall av første behandling:

    HVIS behandling er AVSLUTTET
     OG utfall er en av denne mengden: {STADFESTELSE, AVVIST, ?DELVIS_MEDHOLD?}
     DA skal innstillingsbrev sendes til bruker
       OG status skal settes til PÅ VENT
       OG Ankebehandlingen får en datoverdi for "ventetid påbegynt"
       "Ventetid påbegynt" er utledet av datoverdi for behandling AVSLUTTET

    HVIS behandling er AVSLUTTET
     OG utfall er en av denne mengden: {TRUKKET, OPPHEVET, MEDHOLD, UGUNST}
     DA skal infobrev sendes til bruker
       OG status er AVSLUTTET
       OG Ankbehandlingen anses som ferdig

    RETUR er ikke aktuelt for anker, skal ikke være et valg for saksbehandler

    SEARCH lager en liste med anker på vent basert på statusen PÅ VENT

    Dette fører til opprettelse av andre behandling:
     - Noen trykker på knappen Gjenåpne

     Situasjonen blir at vi har en ankebehandling med en åpen 2. behandling

     */
}