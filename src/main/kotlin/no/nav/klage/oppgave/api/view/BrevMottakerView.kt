package no.nav.klage.oppgave.api.view

data class BrevMottakerView(
    val partId: PartIdView,
    val navn: String?,
    val rolle: String,
)

data class PartIdView(
    val partIdTypeId: String,
    val value: String
)
