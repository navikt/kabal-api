package no.nav.klage.oppgave.api.view

import java.time.LocalDateTime

data class UtfallEditedView(
    val modified: LocalDateTime,
    val utfallId: String?,
    val extraUtfallIdSet: Set<String>
)

data class ExtraUtfallEditedView(
    val modified: LocalDateTime,
    val extraUtfallIdSet: Set<String>
)