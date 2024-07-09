package no.nav.klage.oppgave.api.view

import no.nav.klage.oppgave.domain.klage.SvarbrevSettings
import java.time.LocalDateTime
import java.util.*

data class UpdateSvarbrevSettingsInput(
    val behandlingstidUnitType: SvarbrevSettings.BehandlingstidUnitType,
    val behandlingstidUnits: Int,
    val customText: String?,
    val shouldSend: Boolean,
)

data class SvarbrevSettingsView(
    val id: UUID,
    val ytelseId: String,
    val behandlingstidUnits: Int,
    val behandlingstidUnitType: SvarbrevSettings.BehandlingstidUnitType,
    val customText: String?,
    val shouldSend: Boolean,
    val created: LocalDateTime,
    val modified: LocalDateTime,
    val createdBy: SaksbehandlerView,
)