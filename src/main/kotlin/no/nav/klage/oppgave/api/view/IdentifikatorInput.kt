package no.nav.klage.oppgave.api.view

data class PostnummerInput(
    val postnummer: String
)

data class IdentifikatorInput(
    val identifikator: String
)

data class NullableIdentifikatorInput(
    val identifikator: String?
)