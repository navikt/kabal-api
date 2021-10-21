package no.nav.klage.dokument.api.input

data class DokumentEnhetInput(
    val brevMottakere: List<BrevMottakerInput>,
    //TODO Send inn først ved ferdigstilling?
    val journalfoeringData: JournalfoeringDataInput
)
