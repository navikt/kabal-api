package no.nav.klage.oppgave.clients.kabaldocument.model.request

data class BrevmottakerInput(
    val partId: PartIdInput,
    val navn: String?,
    val rolle: String,
)
