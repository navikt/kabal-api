package no.nav.klage.oppgave.api.view

import no.nav.klage.dokument.api.view.AddressInput

data class IdentifikatorInput(
    val identifikator: String
)

data class SearchPartWithUtsendingskanalInput(
    val identifikator: String,
    val sakenGjelderId: String,
    val ytelseId: String,
)

data class FullmektigInput(
    val identifikator: String?,
    val address: AddressInput?,
    val name: String?,
)