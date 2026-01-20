package no.nav.klage.oppgave.clients.klageunleashproxy

data class UnleashProxyRequest(
    val navIdent: String,
    val appName: String,
    val podName: String,
)