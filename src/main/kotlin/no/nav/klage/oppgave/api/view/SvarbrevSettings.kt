package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.oppgave.domain.klage.SvarbrevSettings
import java.time.LocalDateTime
import java.util.*

data class UpdateSvarbrevSettingsInput(
    val behandlingstidUnitType: TimeUnitType?,
    val behandlingstidUnitTypeId: String?,
    val behandlingstidUnits: Int,
    val customText: String,
    val shouldSend: Boolean,
)

data class SvarbrevSettingsView(
    val id: UUID,
    val ytelseId: String,
    val typeId: String,
    val behandlingstidUnits: Int,
    val behandlingstidUnitType: TimeUnitType,
    val behandlingstidUnitTypeId: String,
    val customText: String?,
    val shouldSend: Boolean,
    val created: LocalDateTime,
    val modified: LocalDateTime,
    val modifiedBy: SaksbehandlerView,
)