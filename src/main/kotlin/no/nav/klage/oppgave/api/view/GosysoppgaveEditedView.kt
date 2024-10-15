package no.nav.klage.oppgave.api.view

data class GosysoppgaveEditedView(
    val modified: java.time.LocalDateTime,
    val gosysoppgave: BehandlingDetaljerView.GosysOppgaveView?
)