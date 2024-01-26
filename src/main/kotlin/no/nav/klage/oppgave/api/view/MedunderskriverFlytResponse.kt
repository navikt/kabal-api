package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.FlowState
import java.time.LocalDateTime

data class MedunderskriverFlowStateResponse (
    val employee: SaksbehandlerView?,
    val navn: String?,
    val navIdent: String?,
    val modified: LocalDateTime,
    val flowState: FlowState,
)

data class MedunderskriverWrapped (
    val employee: SaksbehandlerView?,
    val navIdent: String?,
    val modified: LocalDateTime,
    val flowState: FlowState,
)
