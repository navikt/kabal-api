package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "mottak_hjemmel", schema = "klage")
class MottakHjemmel(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "hjemmel_id")
    val hjemmelId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MottakHjemmel

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
