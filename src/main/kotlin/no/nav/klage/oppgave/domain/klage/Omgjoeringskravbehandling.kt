package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

//const val KLAGEENHET_PREFIX = "42"

@Entity
@DiscriminatorValue("omgjoeringskrav")
class Omgjoeringskravbehandling(
    @Column(name = "klage_behandlende_enhet")
    val klageBehandlendeEnhet: String,
    @Column(name = "mottak_id")
    val mottakId: UUID,
    @Column(name = "dato_innsendt")
    val innsendt: LocalDate? = null,
    @Column(name = "kaka_kvalitetsvurdering_id")
    var kakaKvalitetsvurderingId: UUID?,
    @Column(name = "kaka_kvalitetsvurdering_version", nullable = false)
    val kakaKvalitetsvurderingVersion: Int,
    @Column(name = "varslet_frist")
    var varsletFrist: LocalDate? = null,
    @Column(name = "varslet_behandlingstid_units")
    var varsletBehandlingstidUnits: Int? = null,
    @Column(name = "varslet_behandlingstid_unit_type_id")
    @Convert(converter = TimeUnitTypeConverter::class)
    var varsletBehandlingstidUnitType: TimeUnitType? = null,
    @Column(name = "source_behandling_id")
    var sourceBehandlingId: UUID?,

    //Common properties between klage/anke
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
    frist: LocalDate,
    tildeling: Tildeling? = null,
    created: LocalDateTime = LocalDateTime.now(),
    modified: LocalDateTime = LocalDateTime.now(),
    saksdokumenter: MutableSet<Saksdokument> = mutableSetOf(),
    hjemler: Set<Hjemmel> = emptySet(),
    sattPaaVent: SattPaaVent? = null,
    feilregistrering: Feilregistrering? = null,
    utfall: Utfall? = null,
    extraUtfallSet: Set<Utfall> = emptySet(),
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
    oppgaveReturned: OppgaveReturned? = null,
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
    oppgaveReturned = oppgaveReturned,
) {

    override fun toString(): String {
        return "Omgjoeringskravbehandling(id=$id, " +
                "modified=$modified, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Omgjoeringskravbehandling

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}