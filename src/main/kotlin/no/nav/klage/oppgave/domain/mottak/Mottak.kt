package no.nav.klage.oppgave.domain.mottak

import jakarta.persistence.*
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.FagsystemConverter
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.TypeConverter
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.kodeverk.ytelse.YtelseConverter
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.Prosessfullmektig
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.util.*

@Entity
@Table(name = "mottak", schema = "klage")
class Mottak(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "type_id")
    @Convert(converter = TypeConverter::class)
    val type: Type,
    @Embedded
    val klager: Klager,
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "navn", column = Column(name = "prosessfullmektig_navn")),
        ]
    )
    val prosessfullmektig: Prosessfullmektig?,
    @Embedded
    val sakenGjelder: SakenGjelder?,
    @Column(name = "sak_fagsystem")
    @Convert(converter = FagsystemConverter::class)
    val fagsystem: Fagsystem,
    @Column(name = "sak_fagsak_id")
    val fagsakId: String,
    @Column(name = "kilde_referanse")
    val kildeReferanse: String,
    @Column(name = "dvh_referanse")
    val dvhReferanse: String?,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "mottak_id", referencedColumnName = "id", nullable = false)
    val hjemler: Set<MottakHjemmel>,
    @Column(name = "forrige_saksbehandlerident")
    val forrigeSaksbehandlerident: String?,
    @Column(name = "forrige_behandlende_enhet")
    val forrigeBehandlendeEnhet: String,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "mottak_id", referencedColumnName = "id", nullable = false)
    val mottakDokument: MutableSet<MottakDokument> = mutableSetOf(),
    @Column(name = "brukers_klage_mottatt_vedtaksinstans")
    val brukersKlageMottattVedtaksinstans: LocalDate?,
    @Column(name = "dato_sak_mottatt_klageinstans")
    val sakMottattKaDato: LocalDateTime,
    @Column(name = "dato_frist")
    val frist: LocalDate?,
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
    val forrigeBehandlingId: UUID?,
    @Column(name = "sent_from")
    @Enumerated(EnumType.STRING)
    val sentFrom: Sender

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
        return frist ?: getDefaultFristForType(sakMottattKaDato = sakMottattKaDato, type = type)
    }

    private fun getDefaultFristForType(sakMottattKaDato: LocalDateTime, type: Type): LocalDate {
        return when(type) {
            Type.ANKE -> (sakMottattKaDato.toLocalDate() + Period.ofWeeks(11))
            else -> (sakMottattKaDato.toLocalDate() + Period.ofWeeks(12))
        }
    }

    fun mapToBehandlingHjemler(): Set<Hjemmel> =
        if (hjemler.isEmpty()) {
            error("Hjemler kan ikke være tomme")
        } else {
            hjemler.map { Hjemmel.of(it.hjemmelId) }.toSet()
        }
}
