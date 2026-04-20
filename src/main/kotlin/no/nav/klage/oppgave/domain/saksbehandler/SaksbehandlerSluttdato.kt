package no.nav.klage.oppgave.domain.saksbehandler

import java.time.LocalDate

data class SaksbehandlerSluttdato(
    val navIdent: String,
    val sluttdato: LocalDate?,
)