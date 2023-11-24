package no.nav.klage.oppgave.api.view

import java.time.LocalDateTime

data class SaksbehandlerView(
    val navIdent: String,
    val navn: String
)

data class SaksbehandlerInput(
    val navIdent: String?,
)

data class SetSaksbehandlerInput(
    val navIdent: String?,
    val fradelingReasonId: String?,
)

data class SaksbehandlerViewWrapped(
    val saksbehandler: SaksbehandlerView?,
    val modified: LocalDateTime,
)