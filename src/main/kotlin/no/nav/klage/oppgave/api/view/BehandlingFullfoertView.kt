package no.nav.klage.oppgave.api.view

import java.time.LocalDateTime

data class BehandlingFullfoertView(
    val modified: LocalDateTime,
    val isAvsluttetAvSaksbehandler: Boolean,
)
