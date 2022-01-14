package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.UtfallConverter
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.kodeverk.hjemmel.RegistreringshjemmelConverter
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "vedtak", schema = "klage")
class Vedtak(
    @Id
    val id: UUID = UUID.randomUUID(),
    //Skal overføres til neste delbehandling.
    @Column(name = "utfall_id")
    @Convert(converter = UtfallConverter::class)
    var utfall: Utfall? = null,
    //Registreringshjemler. Overføres til neste delbehandling.
    @ElementCollection(targetClass = Registreringshjemmel::class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "vedtak_hjemmel",
        schema = "klage",
        joinColumns = [JoinColumn(name = "vedtak_id", referencedColumnName = "id", nullable = false)]
    )
    @Convert(converter = RegistreringshjemmelConverter::class)
    @Column(name = "id")
    var hjemler: MutableSet<Registreringshjemmel> = mutableSetOf(),
    @Column(name = "modified")
    var modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    //Overføres ikke. Innstillingsbrev fra første delbehandling vil dukke oppe i saksdokumenter-lista.
    @Column(name = "dokument_enhet_id")
    var dokumentEnhetId: UUID? = null,
    //Vent med vurdering. Mulig det skal være draft på tvers av delbehandlinger.
    @Column(name = "smart_editor_id")
    var smartEditorId: String? = null,
    @Column(name = "hovedadressat_journalpost_id")
    var hovedAdressatJournalpostId: String? = null
) {
    override fun toString(): String {
        return "Vedtak(id=$id, " +
                "modified=$modified, " +
                "created=$created)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Vedtak

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
