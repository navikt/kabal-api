package no.nav.klage.oppgave.api.view

import java.time.LocalDate
import java.time.LocalDateTime

data class KlagebehandlingFullfoertView(
    val modified: LocalDateTime,
    val ferdigstilt: LocalDateTime,
    val avsluttetAvSaksbehandler: LocalDate?
)
