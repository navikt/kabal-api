package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.ytelse.Ytelse

data class GosysOppgaveSearchInput(
    val fnr: String,
    val ytelse: Ytelse,
)

