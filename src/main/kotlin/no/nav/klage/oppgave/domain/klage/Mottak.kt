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
    val hjemler: Set<MottakHjemmel>? = null,
    @Column(name = "forrige_saksbehandlerident")
    val forrigeSaksbehandlerident: String? = null,
    @Column(name = "forrige_behandlende_enhet")
    val forrigeBehandlendeEnhet: String,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "mottak_id", referencedColumnName = "id", nullable = false)
    val mottakDokument: MutableSet<MottakDokument> = mutableSetOf(),
    @Column(name = "dato_innsendt")
    val innsendtDato: LocalDate? = null,
    @Column(name = "dato_brukers_henvendelse_mottatt_nav")
    val brukersHenvendelseMottattNavDato: LocalDate,
    //Denne vil være den samme verdien som brukersHenvendelseMottattNavDato for anke, ikke for klage.
    @Column(name = "dato_sak_mottatt_klageinstans")
    val sakMottattKaDato: LocalDateTime,
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
    val kommentar: String? = null,
    @Column(name = "forrige_vedtak_dato")
    val forrigeVedtakDato: LocalDateTime? = null,
    @Column(name = "forrige_vedtak_id")
    val forrigeVedtakId: UUID? = null,

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

    fun generateFrist(): LocalDate = sakMottattKaDato.toLocalDate() + Period.ofWeeks(12)
}
