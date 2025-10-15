package no.nav.klage.oppgave.domain.behandling

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.embedded.*
import no.nav.klage.oppgave.domain.behandling.historikk.*
import no.nav.klage.oppgave.domain.behandling.subentities.Saksdokument
import no.nav.klage.oppgave.domain.kafka.ExternalUtfall
import org.hibernate.envers.Audited
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Entity
@DiscriminatorValue("anke_i_trygderetten")
@Audited
class AnkeITrygderettenbehandling(
    @Column(name = "sendt_til_trygderetten")
    override var sendtTilTrygderetten: LocalDateTime,
    @Column(name = "kjennelse_mottatt")
    override var kjennelseMottatt: LocalDateTime? = null,
    /** Tatt over av KA mens den er i TR */
    @Column(name = "ny_ankebehandling_ka")
    var nyAnkebehandlingKA: LocalDateTime? = null,
    /** Skal det opprettes ny behandling etter TR har opphevet? */
    @Column(name = "ny_behandling_etter_tr_opphevet")
    override var nyBehandlingEtterTROpphevet: LocalDateTime? = null,
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
    gosysOppgaveId: Long?,
    gosysOppgaveUpdate: GosysOppgaveUpdate? = null,
    tilbakekreving: Boolean = false,
    ignoreGosysOppgave: Boolean = false,
    gosysOppgaveRequired: Boolean,
    initiatingSystem: InitiatingSystem,
) : BehandlingITrygderetten, Behandling(
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

    fun shouldCreateNewAnkebehandling(): Boolean {
        return nyAnkebehandlingKA != null || utfall in utfallToNewAnkebehandling
    }

    fun shouldCreateNewBehandlingEtterTROpphevet(): Boolean {
        return nyBehandlingEtterTROpphevet != null && utfall == Utfall.OPPHEVET
    }

    fun shouldNotCreateNewBehandling(): Boolean {
        return (!shouldCreateNewAnkebehandling() && !shouldCreateNewBehandlingEtterTROpphevet())
    }
}

data class AnkeITrygderettenbehandlingInput(
    val klager: Klager,
    val sakenGjelder: SakenGjelder? = null,
    val prosessfullmektig: Prosessfullmektig?,
    val ytelse: Ytelse,
    val type: Type,
    val kildeReferanse: String,
    val dvhReferanse: String?,
    val fagsystem: Fagsystem,
    val fagsakId: String,
    val sakMottattKlageinstans: LocalDateTime,
    val saksdokumenter: MutableSet<Saksdokument>,
    val innsendingsHjemler: Set<Hjemmel>?,
    val sendtTilTrygderetten: LocalDateTime,
    val registreringsHjemmelSet: Set<Registreringshjemmel>? = null,
    val ankebehandlingUtfall: ExternalUtfall,
    val previousSaksbehandlerident: String?,
    val gosysOppgaveId: Long?,
    val tilbakekreving: Boolean,
    val gosysOppgaveRequired: Boolean,
    val initiatingSystem: Behandling.InitiatingSystem,
    val previousBehandlingId: UUID?,
)