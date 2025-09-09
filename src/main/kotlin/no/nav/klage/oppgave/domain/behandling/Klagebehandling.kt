package no.nav.klage.oppgave.domain.behandling

import jakarta.persistence.*
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.embedded.*
import no.nav.klage.oppgave.domain.behandling.historikk.*
import no.nav.klage.oppgave.domain.behandling.subentities.ForlengetBehandlingstidDraft
import no.nav.klage.oppgave.domain.behandling.subentities.Saksdokument
import org.hibernate.envers.Audited
import org.hibernate.envers.NotAudited
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

const val KLAGEENHET_PREFIX = "42"

@Entity
@DiscriminatorValue("klage")
@Audited
class Klagebehandling(
    //Brukes ikke i anke
    @Column(name = "dato_mottatt_foersteinstans")
    var mottattVedtaksinstans: LocalDate,
    //Vises i GUI.
    @Column(name = "avsender_enhet_foersteinstans")
    val avsenderEnhetFoersteinstans: String,
    //Kommer fra innsending
    @Column(name = "kommentar_fra_foersteinstans")
    val kommentarFraFoersteinstans: String? = null,
    @Column(name = "mottak_id")
    val mottakId: UUID,
    @Column(name = "kaka_kvalitetsvurdering_id")
    var kakaKvalitetsvurderingId: UUID?,
    @Column(name = "kaka_kvalitetsvurdering_version", nullable = false)
    val kakaKvalitetsvurderingVersion: Int,
    @Embedded
    override var varsletBehandlingstid: VarsletBehandlingstid?,
    @OneToOne(cascade = [CascadeType.ALL], optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "forlenget_behandlingstid_draft_id", referencedColumnName = "id")
    @NotAudited
    override var forlengetBehandlingstidDraft: ForlengetBehandlingstidDraft?,

    //Common properties between klage/anke
    id: UUID = UUID.randomUUID(),
    klager: Klager,
    sakenGjelder: SakenGjelder,
    prosessfullmektig: Prosessfullmektig?,
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
    gosysOppgaveId: Long?,
    gosysOppgaveUpdate: GosysOppgaveUpdate? = null,
    tilbakekreving: Boolean = false,
    ignoreGosysOppgave: Boolean = false,
) : BehandlingWithVarsletBehandlingstid, Behandling(
    id = id,
    klager = klager,
    prosessfullmektig = prosessfullmektig,
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
    gosysOppgaveId = gosysOppgaveId,
    gosysOppgaveUpdate = gosysOppgaveUpdate,
    tilbakekreving = tilbakekreving,
    ignoreGosysOppgave = ignoreGosysOppgave,
) {

    override fun toString(): String {
        return "Klagebehandling(id=$id, " +
                "modified=$modified, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Klagebehandling

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}