package no.nav.klage.oppgave.clients.kabaldocument.model.request

data class DokumentEnhetInput(
    val brevMottakere: List<BrevMottakerInput>,
    val journalfoeringData: JournalfoeringDataInput
)
