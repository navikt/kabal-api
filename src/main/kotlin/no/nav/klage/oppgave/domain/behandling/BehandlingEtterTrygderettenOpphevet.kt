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

@Entity
@DiscriminatorValue("behandling_etter_tr_opphevet")
@Audited
class BehandlingEtterTrygderettenOpphevet(
    @Column(name = "kaka_kvalitetsvurdering_id")
    override var kakaKvalitetsvurderingId: UUID?,
    @Column(name = "kaka_kvalitetsvurdering_version", nullable = false)
    override var kakaKvalitetsvurderingVersion: Int,
    @Embedded
    override var varsletBehandlingstid: VarsletBehandlingstid?,
    @Column(name = "source_behandling_id", nullable = false)
    var sourceBehandlingId: UUID,
    @Column(name = "kjennelse_mottatt", nullable = false)
    val kjennelseMottatt: LocalDateTime,
    @Column(name = "anke_behandlende_enhet", nullable = false)
    val ankeBehandlendeEnhet: String,
    @OneToOne(cascade = [CascadeType.ALL], optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "forlenget_behandlingstid_draft_id", referencedColumnName = "id")
    @NotAudited
    override var forlengetBehandlingstidDraft: ForlengetBehandlingstidDraft?,

    //Common properties
    id: UUID = UUID.randomUUID(),
    previousBehandlingId: UUID?,
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
    gosysOppgaveRequired: Boolean,
    initiatingSystem: InitiatingSystem,
) : BehandlingWithVarsletBehandlingstid, BehandlingWithKvalitetsvurdering, Behandling(
    id = id,
    previousBehandlingId = previousBehandlingId,
    klager = klager,
    sakenGjelder = sakenGjelder,
    prosessfullmektig = prosessfullmektig,
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
    gosysOppgaveRequired = gosysOppgaveRequired,
    initiatingSystem = initiatingSystem,
) {

    override fun toString(): String {
        return "BehandlingEtterTrygderettenOpphevet(id=$id, " +
                "modified=$modified, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BehandlingEtterTrygderettenOpphevet

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}