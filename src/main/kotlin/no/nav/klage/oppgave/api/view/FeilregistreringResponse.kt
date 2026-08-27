package no.nav.klage.oppgave.api.view

import java.time.LocalDateTime

data class FeilregistreringResponse(
    val feilregistrering: BehandlingDetaljerView.FeilregistreringView,
    val modified: LocalDateTime,
)
