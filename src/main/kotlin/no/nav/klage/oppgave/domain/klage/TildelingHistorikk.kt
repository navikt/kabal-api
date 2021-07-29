package no.nav.klage.oppgave.domain.klage

import org.springframework.data.domain.Persistable
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "tildelinghistorikk", schema = "klage")
class TildelingHistorikk(
    @Id
    @JvmField
    val id: UUID = UUID.randomUUID(),
    @Embedded
    val tildeling: Tildeling
) : Comparable<TildelingHistorikk>, Persistable<UUID> {

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

        other as TildelingHistorikk

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "TildelingHistorikk(id=$id, tildeling=$tildeling)"
    }

    override fun compareTo(other: TildelingHistorikk): Int {
        return this.tildeling.tidspunkt.compareTo(other.tildeling.tidspunkt)
    }
}
