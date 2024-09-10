package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "behandling", schema = "klage")
@DiscriminatorColumn(name = "behandling_type")
@NamedEntityGraphs(
    NamedEntityGraph(
        name = "Behandling.full",
        attributeNodes = [
            NamedAttributeNode("saksdokumenter"),
            NamedAttributeNode("hjemler"),
            NamedAttributeNode("registreringshjemler"),
            NamedAttributeNode("medunderskriverHistorikk"),
            NamedAttributeNode("tildelingHistorikk"),
            NamedAttributeNode("rolHistorikk"),
            NamedAttributeNode("klagerHistorikk"),
            NamedAttributeNode("fullmektigHistorikk"),
            NamedAttributeNode("sattPaaVentHistorikk"),
            NamedAttributeNode("extraUtfallSet")
        ]
    ),
)
abstract class Behandling(
    @Id
    open val id: UUID = UUID.randomUUID(),
    @Embedded
    open var klager: Klager,
    @Embedded
    open var sakenGjelder: SakenGjelder,
    @Column(name = "ytelse_id")
    @Convert(converter = YtelseConverter::class)
    open val ytelse: Ytelse,
    @Column(name = "type_id")
    @Convert(converter = TypeConverter::class)
    open var type: Type,
    @Column(name = "kilde_referanse")
    open val kildeReferanse: String,
    @Column(name = "dato_mottatt_klageinstans")
    open var mottattKlageinstans: LocalDateTime,
    @Column(name = "modified")
    open var modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created")
    open val created: LocalDateTime = LocalDateTime.now(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    open val tildelingHistorikk: MutableSet<TildelingHistorikk> = mutableSetOf(),
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "saksbehandlerident", column = Column(name = "tildelt_saksbehandlerident")),
            AttributeOverride(name = "enhet", column = Column(name = "tildelt_enhet")),
            AttributeOverride(name = "tidspunkt", column = Column(name = "dato_behandling_tildelt"))
        ]
    )
    open var tildeling: Tildeling? = null,
    @Column(name = "frist")
    open var frist: LocalDate? = null,
    @Column(name = "sak_fagsak_id")
    open val fagsakId: String,
    @Column(name = "sak_fagsystem")
    @Convert(converter = FagsystemConverter::class)
    open val fagsystem: Fagsystem,
    @Column(name = "dvh_referanse")
    open val dvhReferanse: String? = null,
    //Liste med dokumenter fra Joark. De dokumentene saksbehandler krysser av for havner her. Kopierer fra forrige når ny behandling opprettes.
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    open val saksdokumenter: MutableSet<Saksdokument> = mutableSetOf(),
    //Dette er søkehjemler, input fra førsteinstans.
    @ElementCollection(targetClass = Hjemmel::class, fetch = FetchType.LAZY)
    @CollectionTable(
        name = "behandling_hjemmel",
        schema = "klage",
        joinColumns = [JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)]
    )
    @Convert(converter = HjemmelConverter::class)
    @Column(name = "id")
    open var hjemler: Set<Hjemmel> = emptySet(),
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "from", column = Column(name = "satt_paa_vent_from")),
            AttributeOverride(name = "to", column = Column(name = "satt_paa_vent_to")),
            AttributeOverride(name = "reason", column = Column(name = "satt_paa_vent_reason")),
        ]
    )
    open var sattPaaVent: SattPaaVent?,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "navIdent", column = Column(name = "feilregistrering_nav_ident")),
            AttributeOverride(name = "navn", column = Column(name = "feilregistrering_navn")),
            AttributeOverride(name = "registered", column = Column(name = "feilregistrering_registered")),
            AttributeOverride(name = "reason", column = Column(name = "feilregistrering_reason")),
            AttributeOverride(name = "fagsystem", column = Column(name = "feilregistrering_fagsystem_id"))
        ]
    )
    open var feilregistrering: Feilregistrering?,

    @Column(name = "utfall_id")
    @Convert(converter = UtfallConverter::class)
    open var utfall: Utfall? = null,

    @ElementCollection(targetClass = Utfall::class, fetch = FetchType.LAZY)
    @CollectionTable(
        name = "behandling_extra_utfall",
        schema = "klage",
        joinColumns = [JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)]
    )
    @Convert(converter = UtfallConverter::class)
    @Column(name = "id")
    open var extraUtfallSet: Set<Utfall> = setOf(),

    //Overføres til neste behandling.
    @ElementCollection(targetClass = Registreringshjemmel::class, fetch = FetchType.LAZY)
    @CollectionTable(
        name = "behandling_registreringshjemmel",
        schema = "klage",
        joinColumns = [JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)]
    )
    @Convert(converter = RegistreringshjemmelConverter::class)
    @Column(name = "id")
    open var registreringshjemler: MutableSet<Registreringshjemmel> = mutableSetOf(),
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "saksbehandlerident", column = Column(name = "medunderskriverident")),
            AttributeOverride(name = "tidspunkt", column = Column(name = "dato_sendt_medunderskriver"))
        ]
    )
    open var medunderskriver: MedunderskriverTildeling? = null,
    @Column(name = "medunderskriver_flow_state_id")
    @Convert(converter = FlowStateConverter::class)
    open var medunderskriverFlowState: FlowState = FlowState.NOT_SENT,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    open val medunderskriverHistorikk: MutableSet<MedunderskriverHistorikk> = mutableSetOf(),
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "avsluttet", column = Column(name = "dato_behandling_avsluttet")),
            AttributeOverride(
                name = "avsluttetAvSaksbehandler",
                column = Column(name = "dato_behandling_avsluttet_av_saksbehandler")
            ),
            AttributeOverride(name = "navIdent", column = Column(name = "ferdigstilling_nav_ident")),
            AttributeOverride(name = "navn", column = Column(name = "ferdigstilling_navn")),
        ]
    )
    open var ferdigstilling: Ferdigstilling?,
    @Column(name = "rol_ident")
    open var rolIdent: String?,
    @Column(name = "rol_flow_state_id")
    @Convert(converter = FlowStateConverter::class)
    open var rolFlowState: FlowState = FlowState.NOT_SENT,
    @Column(name = "rol_returned_date")
    open var rolReturnedDate: LocalDateTime?,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    open val rolHistorikk: MutableSet<RolHistorikk> = mutableSetOf(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    open val klagerHistorikk: MutableSet<KlagerHistorikk> = mutableSetOf(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    open val fullmektigHistorikk: MutableSet<FullmektigHistorikk> = mutableSetOf(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    open val sattPaaVentHistorikk: MutableSet<SattPaaVentHistorikk> = mutableSetOf(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "behandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    open val varsletBehandlingstidHistorikk: MutableSet<VarsletBehandlingstidHistorikk> = mutableSetOf(),
    @Column(name = "previous_saksbehandlerident")
    open val previousSaksbehandlerident: String?,
    @Column(name = "oppgave_id")
    open var oppgaveId: Long?,
) {

    /**
     * Brukes til ES og statistikk per nå
     */
    fun getStatus(): Status {
        return when {
            feilregistrering != null -> Status.FEILREGISTRERT
            ferdigstilling?.avsluttet != null -> Status.FULLFOERT
            ferdigstilling?.avsluttetAvSaksbehandler != null -> Status.AVSLUTTET_AV_SAKSBEHANDLER
            sattPaaVent != null -> Status.SATT_PAA_VENT
            medunderskriverFlowState == FlowState.SENT -> Status.SENDT_TIL_MEDUNDERSKRIVER
            medunderskriverFlowState == FlowState.RETURNED -> Status.RETURNERT_TIL_SAKSBEHANDLER
            medunderskriver?.saksbehandlerident != null -> Status.MEDUNDERSKRIVER_VALGT
            tildeling?.saksbehandlerident != null -> Status.TILDELT
            tildeling?.saksbehandlerident == null -> Status.IKKE_TILDELT
            else -> Status.UKJENT
        }
    }

    enum class Status {
        IKKE_TILDELT, TILDELT, MEDUNDERSKRIVER_VALGT, SENDT_TIL_MEDUNDERSKRIVER, RETURNERT_TIL_SAKSBEHANDLER, AVSLUTTET_AV_SAKSBEHANDLER, SATT_PAA_VENT, FULLFOERT, UKJENT, FEILREGISTRERT
    }

    fun shouldBeSentToTrygderetten(): Boolean {
        return utfall in utfallToTrygderetten
    }

    fun shouldCreateNewAnkebehandling(): Boolean {
        return if (this is AnkeITrygderettenbehandling) {
            nyAnkebehandlingKA != null || utfall in utfallToNewAnkebehandling
        } else {
            false
        }
    }

    fun shouldCreateNewBehandlingEtterTROpphevet(): Boolean {
        return if (this is AnkeITrygderettenbehandling) {
            nyBehandlingEtterTROpphevet != null && utfall == Utfall.OPPHEVET
        } else {
            false
        }
    }

    fun getRoleInBehandling(innloggetIdent: String) = if (rolIdent == innloggetIdent) {
        BehandlingRole.KABAL_ROL
    } else if (tildeling?.saksbehandlerident == innloggetIdent) {
        BehandlingRole.KABAL_SAKSBEHANDLING
    } else if (medunderskriver?.saksbehandlerident == innloggetIdent) {
        BehandlingRole.KABAL_MEDUNDERSKRIVER
    } else BehandlingRole.NONE
}

enum class BehandlingRole {
    KABAL_SAKSBEHANDLING,
    KABAL_ROL,
    KABAL_MEDUNDERSKRIVER,
    NONE,
    ;
}


val utfallToNewAnkebehandling = setOf(
    Utfall.HENVIST
)

val utfallToTrygderetten = setOf(
    Utfall.DELVIS_MEDHOLD,
    Utfall.INNSTILLING_AVVIST,
    Utfall.INNSTILLING_STADFESTELSE
)

val noRegistringshjemmelNeeded = listOf(Utfall.TRUKKET, Utfall.RETUR)
val noKvalitetsvurderingNeeded = listOf(Utfall.TRUKKET, Utfall.RETUR, Utfall.UGUNST)