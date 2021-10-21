package no.nav.klage.dokument.api.input

import no.nav.klage.oppgave.clients.kabaldocument.model.request.TilleggsopplysningInput

data class JournalfoeringDataInput(
    val sakenGjelder: PartIdInput,
    val tema: String,
    val sakFagsakId: String?,
    val sakFagsystem: String?,
    val kildeReferanse: String,
    val enhet: String,
    val behandlingstema: String,
    val tittel: String,
    val brevKode: String,
    val tilleggsopplysning: TilleggsopplysningInput?
)
