package no.nav.klage.oppgave.api.view

data class VedtakUtfallInput(
    val utfall: String?
)

data class VedtakHjemlerInput(
    val hjemler: Set<String>?
)