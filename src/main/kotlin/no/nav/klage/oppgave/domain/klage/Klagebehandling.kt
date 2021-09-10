package no.nav.klage.oppgave.domain.klage

import no.nav.klage.oppgave.domain.klage.Klagebehandling.Status.*
import no.nav.klage.oppgave.domain.kodeverk.*
import no.nav.klage.oppgave.exceptions.KlagebehandlingSamtidigEndretException
import no.nav.klage.oppgave.exceptions.VedtakNotFoundException
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
    @Version
    @Column(name = "versjon")
    val versjon: Long = 1L,
    @Embedded
    var klager: Klager,
    @Embedded
    var sakenGjelder: SakenGjelder,
    @Column(name = "tema_id")
    @Convert(converter = TemaConverter::class)
    var tema: Tema,
    @Column(name = "type_id")
    @Convert(converter = TypeConverter::class)
    var type: Type,
    @Column(name = "referanse_id")
    var kildeReferanse: String? = null,
    @Column(name = "sak_fagsystem")
    @Convert(converter = FagsystemConverter::class)
    var sakFagsystem: Fagsystem? = null,
    @Column(name = "sak_fagsak_id")
    var sakFagsakId: String? = null,
    @Column(name = "dato_innsendt")
    var innsendt: LocalDate? = null,
    @Column(name = "dato_mottatt_foersteinstans")
    var mottattFoersteinstans: LocalDate? = null,
    @Column(name = "avsender_saksbehandlerident_foersteinstans")
    var avsenderSaksbehandleridentFoersteinstans: String? = null,
    @Column(name = "avsender_enhet_foersteinstans")
    var avsenderEnhetFoersteinstans: String? = null,
    @Column(name = "dato_mottatt_klageinstans")
    var mottattKlageinstans: LocalDateTime,
    @Column(name = "dato_behandling_avsluttet")
    var avsluttet: LocalDateTime? = null,
    @Column(name = "dato_behandling_avsluttet_av_saksbehandler")
    var avsluttetAvSaksbehandler: LocalDateTime? = null,
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
    val tildelingHistorikk: MutableSet<TildelingHistorikk> = mutableSetOf(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "klagebehandling_id", referencedColumnName = "id", nullable = false)
    val medunderskriverHistorikk: MutableSet<MedunderskriverHistorikk> = mutableSetOf(),
    @Column(name = "mottak_id")
    val mottakId: UUID,
    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "kvalitetsvurdering_id", nullable = true)
    var kvalitetsvurdering: Kvalitetsvurdering? = null,

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
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "klagebehandling_id", referencedColumnName = "id", nullable = false)
    val vedtak: MutableSet<Vedtak> = mutableSetOf(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "klagebehandling_id", referencedColumnName = "id", nullable = false)
    val saksdokumenter: MutableSet<Saksdokument> = mutableSetOf(),
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "modified")
    var modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "kildesystem")
    @Convert(converter = FagsystemConverter::class)
    val kildesystem: Fagsystem,
    @Column(name = "kommentar_fra_foersteinstans")
    val kommentarFraFoersteinstans: String? = null,
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

    fun checkOptimisticLocking(klagebehandlingVersjon: Long?) {
        if (klagebehandlingVersjon != null) {
            if (klagebehandlingVersjon != this.versjon) throw KlagebehandlingSamtidigEndretException("Angitt versjon er $klagebehandlingVersjon, men nyeste klagebehandling har versjon ${this.versjon}")
        }
    }

    fun getVedtak(vedtakId: UUID): Vedtak {
        return vedtak.firstOrNull { it.id == vedtakId }
            ?: throw VedtakNotFoundException("Vedtak med id $vedtakId ikke funnet")
    }

    fun lagBrevmottakereForVedtak(vedtakId: UUID) {
        val vedtak = getVedtak(vedtakId)
        val klager = klager
        if (klager.prosessfullmektig != null) {
            vedtak.leggTilProsessfullmektigSomBrevmottaker(klager.prosessfullmektig)
            if (klager.prosessfullmektig.skalPartenMottaKopi) {
                vedtak.leggTilKlagerSomBrevmottaker(klager)
            }
        } else {
            vedtak.leggTilKlagerSomBrevmottaker(klager)
        }
        val sakenGjelder = sakenGjelder
        if (sakenGjelder.partId != klager.partId && sakenGjelder.skalMottaKopi) {
            vedtak.leggTilSakenGjelderSomBrevmottaker(sakenGjelder)
        }
    }

    /**
     * Brukes til ES og statistikk per nå
     */
    fun getStatus(): Status {
        return when {
            avsluttet != null -> FULLFOERT
            avsluttetAvSaksbehandler != null -> GODKJENT_AV_MEDUNDERSKRIVER
            medunderskriver?.saksbehandlerident != null -> SENDT_TIL_MEDUNDERSKRIVER
            tildeling?.saksbehandlerident != null -> TILDELT
            tildeling?.saksbehandlerident == null -> IKKE_TILDELT
            else -> UKJENT
        }
    }

    enum class Status {
        IKKE_TILDELT, TILDELT, SENDT_TIL_MEDUNDERSKRIVER, GODKJENT_AV_MEDUNDERSKRIVER, FULLFOERT, UKJENT
    }
}
