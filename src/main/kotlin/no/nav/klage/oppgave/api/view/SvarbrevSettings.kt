package no.nav.klage.oppgave.api.view

import java.time.LocalDateTime
import java.util.*

data class UpdateSvarbrevSettingsInput(
    val behandlingstidWeeks: Int,
    val customText: String?,
    val shouldSend: Boolean,
)

data class SvarbrevSettingsView(
    val id: UUID,
    val ytelseId: String,
    val behandlingstidWeeks: Int,
    val customText: String?,
    val shouldSend: Boolean,
    val created: LocalDateTime,
    val modified: LocalDateTime,
    val createdBy: SaksbehandlerView,
)