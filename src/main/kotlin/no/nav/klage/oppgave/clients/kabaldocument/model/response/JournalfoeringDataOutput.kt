package no.nav.klage.oppgave.clients.kabaldocument.model.response

data class JournalfoeringDataOutput(
    val sakenGjelder: PartIdOutput,
    val tema: String,
    val sakFagsakId: String?,
    val sakFagsystem: String?,
    val kildeReferanse: String,
    val enhet: String,
)
