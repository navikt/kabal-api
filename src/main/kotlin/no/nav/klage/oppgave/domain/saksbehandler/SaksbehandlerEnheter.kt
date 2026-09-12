package no.nav.klage.oppgave.domain.saksbehandler

data class SaksbehandlerEnheter(
    val navIdent: String,
    val enheter: List<SaksbehandlerEnhet>,
)
