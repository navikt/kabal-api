package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.HjemmelConverter
import no.nav.klage.oppgave.domain.klage.Klagebehandling.Status.*
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

const val KLAGEENHET_PREFIX = "42"

@Entity
@Table(name = "klagebehandling", schema = "klage")
class Klagebehandling(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Embedded
    var klager: Klager,
    @Embedded
    var sakenGjelder: SakenGjelder,
    @Column(name = "ytelse_id")
    @Convert(converter = YtelseConverter::class)
    val ytelse: Ytelse,
    @Column(name = "type_id")
    @Convert(converter = TypeConverter::class)
    var type: Type,
    @Column(name = "kilde_referanse")
    val kildeReferanse: String,
    @Column(name = "dvh_referanse")
    val dvhReferanse: String? = null,
    @Column(name = "sak_fagsystem")
    @Convert(converter = FagsystemConverter::class)
    val sakFagsystem: Fagsystem? = null,
    @Column(name = "sak_fagsak_id")
    val sakFagsakId: String? = null,
    @Column(name = "dato_innsendt")
    val innsendt: LocalDate? = null,
    @Column(name = "dato_mottatt_foersteinstans")
    val mottattFoersteinstans: LocalDate,
    @Column(name = "avsender_saksbehandlerident_foersteinstans")
    val avsenderSaksbehandleridentFoersteinstans: String? = null,
    @Column(name = "avsender_enhet_foersteinstans")
    val avsenderEnhetFoersteinstans: String,
    @Column(name = "dato_mottatt_klageinstans")
    val mottattKlageinstans: LocalDateTime,
    @Column(name = "dato_behandling_avsluttet")
    var avsluttet: LocalDateTime? = null,
    @Column(name = "dato_behandling_avsluttet_av_saksbehandler")
    var avsluttetAvSaksbehandler: LocalDateTime? = null,
    //TODO: Trenger denne være nullable? Den blir da alltid satt i createKlagebehandlingFromMottak?
    @Column(name = "frist")
    var frist: LocalDate? = null,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "saksbehandlerident", column = Column(name = "medunderskriverident")),
            AttributeOverride(name = "tidspunkt", column = Column(name = "dato_sendt_medunderskriver"))
        ]
    )
    var medunderskriver: MedunderskriverTildeling? = null,
    @Column(name = "medunderskriverflyt_id")
    @Convert(converter = MedunderskriverflytConverter::class)
    var medunderskriverFlyt: MedunderskriverFlyt = MedunderskriverFlyt.IKKE_SENDT,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "saksbehandlerident", column = Column(name = "tildelt_saksbehandlerident")),
            AttributeOverride(name = "enhet", column = Column(name = "tildelt_enhet")),
            AttributeOverride(name = "tidspunkt", column = Column(name = "dato_behandling_tildelt"))
        ]
    )
    var tildeling: Tildeling? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "klagebehandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    val tildelingHistorikk: MutableSet<TildelingHistorikk> = mutableSetOf(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "klagebehandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    val medunderskriverHistorikk: MutableSet<MedunderskriverHistorikk> = mutableSetOf(),
    @Column(name = "mottak_id")
    val mottakId: UUID,
    @Column(name = "kvalitetsvurdering_id", nullable = true)
    var kvalitetsvurderingId: UUID? = null,
    @Column(name = "kaka_kvalitetsvurdering_id", nullable = true)
    var kakaKvalitetsvurderingId: UUID? = null,

    //Not in use?
    @ElementCollection(targetClass = Hjemmel::class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "klagebehandling_hjemmel",
        schema = "klage",
        joinColumns = [JoinColumn(name = "klagebehandling_id", referencedColumnName = "id", nullable = false)]
    )
    @Convert(converter = HjemmelConverter::class)
    @Column(name = "id")
    val hjemler: MutableSet<Hjemmel> = mutableSetOf(),
    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "vedtak_id", referencedColumnName = "id")
    val vedtak: Vedtak,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "klagebehandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    val saksdokumenter: MutableSet<Saksdokument> = mutableSetOf(),
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "modified")
    var modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "kildesystem")
    @Convert(converter = FagsystemConverter::class)
    val kildesystem: Fagsystem,
    @Column(name = "kommentar_fra_foersteinstans")
    val kommentarFraFoersteinstans: String? = null
) {

    override fun toString(): String {
        return "Behandling(id=$id, " +
                "modified=$modified, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Klagebehandling

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    /**
     * Brukes til ES og statistikk per nå
     */
    fun getStatus(): Status {
        return when {
            avsluttet != null -> FULLFOERT
            avsluttetAvSaksbehandler != null -> AVSLUTTET_AV_SAKSBEHANDLER
            medunderskriverFlyt == MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER -> SENDT_TIL_MEDUNDERSKRIVER
            medunderskriverFlyt == MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER -> RETURNERT_TIL_SAKSBEHANDLER
            medunderskriver?.saksbehandlerident != null -> MEDUNDERSKRIVER_VALGT
            tildeling?.saksbehandlerident != null -> TILDELT
            tildeling?.saksbehandlerident == null -> IKKE_TILDELT
            else -> UKJENT
        }
    }

    enum class Status {
        IKKE_TILDELT, TILDELT, MEDUNDERSKRIVER_VALGT, SENDT_TIL_MEDUNDERSKRIVER, RETURNERT_TIL_SAKSBEHANDLER, AVSLUTTET_AV_SAKSBEHANDLER, FULLFOERT, UKJENT
    }
}
