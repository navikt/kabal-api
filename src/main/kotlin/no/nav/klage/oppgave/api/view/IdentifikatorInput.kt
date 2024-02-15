package no.nav.klage.oppgave.api.view

data class IdentifikatorInput(
    val identifikator: String
)

data class SearchPartWithUtsendingskanalInput(
    val identifikator: String,
    val sakenGjelderId: String,
    val ytelseId: String,
)

data class NullableIdentifikatorInput(
    val identifikator: String?
)