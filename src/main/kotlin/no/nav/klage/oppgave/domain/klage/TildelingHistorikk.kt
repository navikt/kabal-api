package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "tildelinghistorikk", schema = "klage")
class TildelingHistorikk(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Embedded
    val tildeling: TildelingWithReason,
) : Comparable<TildelingHistorikk> {
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
