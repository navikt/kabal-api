package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.FlowState
import java.time.LocalDateTime

data class RolView(
    val employee: SaksbehandlerView?,
    val flowState: FlowState,
    val modified: LocalDateTime,
)

data class Rols(val rols: List<Rol>) {
    data class Rol(val navIdent: String, val navn: String)
}