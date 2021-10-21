package no.nav.klage.dokument.api.input

data class BrevMottakerInput(
    val partId: PartIdInput,
    val navn: String?,
    val rolle: String,
)
