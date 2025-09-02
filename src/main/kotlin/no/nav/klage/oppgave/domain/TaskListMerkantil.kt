package no.nav.klage.oppgave.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "task_list_merkantil", schema = "klage")
class TaskListMerkantil(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "behandling_id")
    val behandlingId: UUID,
    @Column(name = "reason")
    val reason: String,
    @Column(name = "created")
    val created: LocalDateTime,
    @Column(name = "date_handled")
    var dateHandled: LocalDateTime?,
    @Column(name = "handled_by")
    var handledBy: String?,
    @Column(name = "handled_by_name")
    var handledByName: String?,
    @Column(name = "comment")
    var comment: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TaskListMerkantil

        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "TaskListMerkantil(id=$id, behandlingId=$behandlingId, reason=$reason, created=$created, dateHandled=$dateHandled, handledBy=$handledBy, handledByName=$handledByName, comment=$comment)"
    }
}