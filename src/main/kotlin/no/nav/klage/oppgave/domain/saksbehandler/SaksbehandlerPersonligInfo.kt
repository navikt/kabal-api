package no.nav.klage.oppgave.domain.saksbehandler

data class SaksbehandlerPersonligInfo(
    val navIdent: String,
    val azureId: String,
    val fornavn: String,
    val etternavn: String,
    val sammensattNavn: String,
    val epost: String?,
    val enhet: Enhet,
)