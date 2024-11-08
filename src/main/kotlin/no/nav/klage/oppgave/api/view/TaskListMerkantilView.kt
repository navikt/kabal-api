package no.nav.klage.oppgave.api.view

import java.time.LocalDateTime
import java.util.*

data class TaskListMerkantilView (
    val id: UUID,
    val behandlingId: UUID,
    val reason: String,
    val created: LocalDateTime,
    val dateHandled: LocalDateTime?,
    val handledBy: String?,
    val handledByName: String?,
    var comment: String?,
    var typeId: String,
)