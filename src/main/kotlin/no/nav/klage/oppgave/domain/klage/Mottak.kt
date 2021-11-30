package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "mottak", schema = "klage")
class Mottak(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "tema_id")
    @Convert(converter = TemaConverter::class)
    val tema: Tema? = null,
    @Column(name = "type_id")
    @Convert(converter = TypeConverter::class)
    val type: Type,
    @Embedded
    val klager: Klager,
    @Embedded
    val sakenGjelder: SakenGjelder? = null,
    @Column(name = "sak_fagsystem")
    @Convert(converter = FagsystemConverter::class)
    val sakFagsystem: Fagsystem? = null,
    @Column(name = "sak_fagsak_id")
    val sakFagsakId: String? = null,
    @Column(name = "kilde_referanse")
    val kildeReferanse: String,
    @Column(name = "dvh_referanse")
    val dvhReferanse: String? = null,
    @Column(name = "innsyn_url")
    val innsynUrl: String? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "mottak_id", referencedColumnName = "id", nullable = false)
    val hjemmelListe: MutableSet<MottakHjemmel>? = null,
    @Column(name = "avsender_saksbehandlerident")
    val avsenderSaksbehandlerident: String? = null,
    @Column(name = "avsender_enhet")
    val avsenderEnhet: String,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "mottak_id", referencedColumnName = "id", nullable = false)
    val mottakDokument: MutableSet<MottakDokument> = mutableSetOf(),
    @Column(name = "dato_innsendt")
    val innsendtDato: LocalDate? = null,
    @Column(name = "dato_mottatt_foersteinstans")
    val mottattNavDato: LocalDate,
    @Column(name = "dato_oversendt_klageinstans")
    val oversendtKaDato: LocalDateTime,
    @Column(name = "dato_frist_fra_foersteinstans")
    val fristFraFoersteinstans: LocalDate? = null,
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "modified")
    val modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "kildesystem")
    @Convert(converter = FagsystemConverter::class)
    val kildesystem: Fagsystem,
    @Convert(converter = YtelseConverter::class)
    @Column(name = "ytelse_id")
    val ytelse: Ytelse,
    @Column(name = "kommentar")
    val kommentar: String? = null
) {

    override fun toString(): String {
        return "Mottak(id=$id, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Mottak

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun generateFrist(): LocalDate = oversendtKaDato.toLocalDate() + Period.ofWeeks(12)
}
