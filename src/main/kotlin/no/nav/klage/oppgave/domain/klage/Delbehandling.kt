package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.MedunderskriverFlyt
import no.nav.klage.kodeverk.MedunderskriverflytConverter
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.UtfallConverter
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.kodeverk.hjemmel.RegistreringshjemmelConverter
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "delbehandling", schema = "klage")
class Delbehandling(
    @Id
    val id: UUID = UUID.randomUUID(),
    //Skal overføres til neste delbehandlinger.
    @Column(name = "utfall_id")
    @Convert(converter = UtfallConverter::class)
    var utfall: Utfall? = null,
    //Registreringshjemler. Overføres til neste delbehandlinger.
    @ElementCollection(targetClass = Registreringshjemmel::class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "delbehandling_registreringshjemmel",
        schema = "klage",
        joinColumns = [JoinColumn(name = "delbehandling_id", referencedColumnName = "id", nullable = false)]
    )
    @Convert(converter = RegistreringshjemmelConverter::class)
    @Column(name = "id")
    var hjemler: MutableSet<Registreringshjemmel> = mutableSetOf(),
    @Column(name = "modified")
    var modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    //Overføres ikke. Innstillingsbrev fra første delbehandlinger vil dukke oppe i saksdokumenter-lista.
    @Column(name = "dokument_enhet_id")
    var dokumentEnhetId: UUID? = null,
    //Vent med vurdering. Mulig det skal være draft på tvers av delbehandlingerer.
    @Column(name = "smart_editor_id")
    var smartEditorId: String? = null,
    @Column(name = "hovedadressat_journalpost_id")
    var hovedAdressatJournalpostId: String? = null,
    //Hører hjemme på delbehandlinger
    @Embedded
    @AttributeOverrides(
        value = [
            AttributeOverride(name = "saksbehandlerident", column = Column(name = "medunderskriverident")),
            AttributeOverride(name = "tidspunkt", column = Column(name = "dato_sendt_medunderskriver"))
        ]
    )
    var medunderskriver: MedunderskriverTildeling? = null,
    //Hører hjemme på delbehandlinger
    @Column(name = "medunderskriverflyt_id")
    @Convert(converter = MedunderskriverflytConverter::class)
    var medunderskriverFlyt: MedunderskriverFlyt = MedunderskriverFlyt.IKKE_SENDT,
    //Hører hjemme på delbehandlinger
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "klagebehandling_id", referencedColumnName = "id", nullable = false)
    @Fetch(FetchMode.SELECT)
    @BatchSize(size = 100)
    val medunderskriverHistorikk: MutableSet<MedunderskriverHistorikk> = mutableSetOf(),
) {
    override fun toString(): String {
        return "Delbehandling(id=$id, " +
                "modified=$modified, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Delbehandling

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
