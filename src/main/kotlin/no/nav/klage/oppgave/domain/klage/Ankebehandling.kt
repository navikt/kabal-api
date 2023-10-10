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
@DiscriminatorValue("anke")
class Ankebehandling(
    @Column(name = "klage_vedtaks_dato")
    val klageVedtaksDato: LocalDate? = null,
    @Column(name = "klage_behandlende_enhet")
    val klageBehandlendeEnhet: String,
    //Fins i noen tilfeller, men ikke alle.
    @Column(name = "klage_id")
    var klagebehandlingId: UUID? = null,
    @Column(name = "mottak_id")
    val mottakId: UUID? = null,
    @Column(name = "dato_innsendt")
    val innsendt: LocalDate? = null,
    @Column(name = "kaka_kvalitetsvurdering_id")
    var kakaKvalitetsvurderingId: UUID?,
    @Column(name = "kaka_kvalitetsvurdering_version", nullable = true)
    val kakaKvalitetsvurderingVersion: Int,

//    Finn ut hvordan dette skal fungere i anker etter hvert
//    @Column(name = "dato_behandling_avsluttet_av_saksbehandler")
//    var avsluttetAvSaksbehandler: LocalDateTime? = null,


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
    tildelingHistorikk: MutableSet<TildelingHistorikk> = mutableSetOf(),
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
    medunderskriverHistorikk: MutableSet<MedunderskriverHistorikk> = mutableSetOf(),
    avsluttet: LocalDateTime? = null,
    avsluttetAvSaksbehandler: LocalDateTime? = null,
    rolIdent: String? = null,
    rolFlowState: FlowState = FlowState.NOT_SENT,
    rolReturnedDate: LocalDateTime? = null,
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
    tildelingHistorikk = tildelingHistorikk,
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
    medunderskriverHistorikk = medunderskriverHistorikk,
    avsluttet = avsluttet,
    avsluttetAvSaksbehandler = avsluttetAvSaksbehandler,
    rolIdent = rolIdent,
    rolFlowState = rolFlowState,
    rolReturnedDate = rolReturnedDate,
) {
    override fun toString(): String {
        return "Ankebehandling(id=$id, " +
                "modified=$modified, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Ankebehandling

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}