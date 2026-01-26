package no.nav.klage.oppgave.domain.behandling.subentities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "melding", schema = "klage")
class Melding(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "behandling_id", nullable = false)
    val behandlingId: UUID,
    @Column(name = "text", nullable = false)
    var text: String,
    @Column(name = "saksbehandlerident", nullable = false)
    val saksbehandlerident: String,
    @Column(name = "created", nullable = false)
    val created: LocalDateTime,
    @Column(name = "modified")
    var modified: LocalDateTime? = null,
    @Column(name = "notify", nullable = false)
    var notify: Boolean,
) : Comparable<Melding> {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Melding

        return id == other.id
    }

    override fun toString(): String {
        return "Melding(id=$id, behandlingId=$behandlingId, text='$text', saksbehandlerident='$saksbehandlerident', created=$created, modified=$modified, notify=$notify)"
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun compareTo(other: Melding): Int {
        return this.created.compareTo(other.created)
    }
}