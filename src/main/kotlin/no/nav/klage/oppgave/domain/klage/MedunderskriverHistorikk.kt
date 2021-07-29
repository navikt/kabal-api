package no.nav.klage.oppgave.domain.klage

import org.springframework.data.domain.Persistable
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "medunderskriverhistorikk", schema = "klage")
class MedunderskriverHistorikk(
    @Id
    @JvmField
    val id: UUID = UUID.randomUUID(),
    @Embedded
    val medunderskriver: MedunderskriverTildeling
) : Comparable<MedunderskriverHistorikk>, Persistable<UUID> {

    override fun getId(): UUID = id

    @Transient
    private var isNew = true

    override fun isNew(): Boolean {
        return isNew
    }

    @PrePersist
    @PostLoad
    fun markNotNew() {
        isNew = false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MedunderskriverHistorikk

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "MedunderskriverHistorikk(id=$id, medunderskriver=$medunderskriver)"
    }

    override fun compareTo(other: MedunderskriverHistorikk): Int {
        return this.medunderskriver.tidspunkt.compareTo(other.medunderskriver.tidspunkt)
    }
}
