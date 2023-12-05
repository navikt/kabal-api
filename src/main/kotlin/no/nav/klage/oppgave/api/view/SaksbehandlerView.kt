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
    val navIdent: String,
)

data class FradelSaksbehandlerInput(
    val reasonId: String,
    val hjemmelIdList: List<String>?,
)

data class SaksbehandlerViewWrapped(
    val saksbehandler: SaksbehandlerView?,
    val modified: LocalDateTime,
)

data class FradeltSaksbehandlerViewWrapped(
    val modified: LocalDateTime,
    val hjemmelIdList: List<String>,
)