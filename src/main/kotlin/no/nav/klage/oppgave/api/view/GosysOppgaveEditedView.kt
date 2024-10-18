package no.nav.klage.oppgave.api.view

data class GosysOppgaveEditedView(
    val modified: java.time.LocalDateTime,
    val gosysOppgave: GosysOppgaveView?
)