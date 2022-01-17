package no.nav.klage.oppgave.domain.klage

import java.time.LocalDateTime
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "melding", schema = "klage")
class Melding(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "behandling_id")
    val klagebehandlingId: UUID,
    @Column(name = "text")
    var text: String,
    @Column(name = "saksbehandlerident")
    val saksbehandlerident: String,
    @Column(name = "created")
    val created: LocalDateTime,
    @Column(name = "modified")
    var modified: LocalDateTime? = null,
) : Comparable<Melding> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Melding

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "Melding(id=$id, text=$text, created=$created, modified=$modified)"
    }

    override fun compareTo(other: Melding): Int {
        return this.created.compareTo(other.created)
    }
}
