package no.nav.klage.oppgave.clients.kabaldocument.model.response

import java.time.LocalDateTime

data class DokumentEnhetOutput(
    val id: String,
    val eier: String,
    val journalfoeringData: JournalfoeringDataOutput,
    val brevMottakere: List<BrevmottakerOutput>,
    val hovedDokument: OpplastetDokumentOutput?,
    val vedlegg: List<OpplastetDokumentOutput>,
    val brevMottakerDistribusjoner: List<BrevmottakerDistribusjonOutput>,
    val avsluttet: LocalDateTime?,
    val modified: LocalDateTime,
    val journalpostIdHovedadressat: String?
)
