package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.*
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Tema
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.util.*

@Entity
@Table(name = "mottak", schema = "klage")
class Mottak(
    @Id
    val id: UUID = UUID.randomUUID(),
    //Ikke i bruk
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
    val fagsystem: Fagsystem,
    @Column(name = "sak_fagsak_id")
    val fagsakId: String,
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
    @Column(name = "dato_frist")
    val frist: LocalDate? = null,
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "modified")
    val modified: LocalDateTime = LocalDateTime.now(),
    @Convert(converter = YtelseConverter::class)
    @Column(name = "ytelse_id")
    val ytelse: Ytelse,
    @Column(name = "kommentar")
    val kommentar: String?,
    @Column(name = "forrige_behandling_id")
    val forrigeBehandlingId: UUID? = null,
    @Column(name = "sent_from")
    @Enumerated(EnumType.STRING)
    val sentFrom: Sender = Sender.FAGSYSTEM

) {

    enum class Sender {
        FAGSYSTEM, KABIN, BRUKER
    }

    override fun toString(): String {
        return "Mottak(id=$id, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Mottak

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    fun generateFrist(): LocalDate {
        return frist ?: (sakMottattKaDato.toLocalDate() + Period.ofWeeks(12))
    }
}
