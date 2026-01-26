package no.nav.klage.oppgave.clients.klagelookup

data class AccessRequest(
    val brukerId: String,
    val navIdent: String?,
)