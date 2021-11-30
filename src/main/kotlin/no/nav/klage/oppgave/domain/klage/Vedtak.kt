package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.Hjemmel
import no.nav.klage.kodeverk.HjemmelConverter
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.UtfallConverter
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "vedtak", schema = "klage")
class Vedtak(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "utfall_id")
    @Convert(converter = UtfallConverter::class)
    var utfall: Utfall? = null,
    @ElementCollection(targetClass = Hjemmel::class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "vedtak_hjemmel",
        schema = "klage",
        joinColumns = [JoinColumn(name = "vedtak_id", referencedColumnName = "id", nullable = false)]
    )
    @Convert(converter = HjemmelConverter::class)
    @Column(name = "id")
    var hjemler: MutableSet<Hjemmel> = mutableSetOf(),
    @Column(name = "modified")
    var modified: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created")
    val created: LocalDateTime = LocalDateTime.now(),
    @Column(name = "dokument_enhet_id")
    var dokumentEnhetId: UUID? = null,
    @Column(name = "smart_editor_id")
    var smartEditorId: String? = null,
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
