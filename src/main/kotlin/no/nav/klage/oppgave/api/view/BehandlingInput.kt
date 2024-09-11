package no.nav.klage.oppgave.api.view

import java.time.LocalDate

data class BehandlingDateInput(
    val date: LocalDate
)

data class BehandlingDateNullableInput(
    val date: LocalDate?
)

data class SattPaaVentInput(
    val to: LocalDate,
    val reason: String
)

data class ReturnOppgaveInput(
    val tildeltEnhet: String,
    val mappeId: Long?,
    val kommentar: String,
)
