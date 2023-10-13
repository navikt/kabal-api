package no.nav.klage.oppgave.clients.kabaldocument.model.request

data class DokumentEnhetWithDokumentreferanserInput(
    val brevMottakere: List<BrevmottakerInput>,
    val journalfoeringData: JournalfoeringDataInput,
    val dokumentreferanser: DokumentInput,
    val dokumentTypeId: String,
    val journalfoerendeSaksbehandlerIdent: String,
) {
    data class DokumentInput(
        val hoveddokument: Dokument,
        val vedlegg: List<Dokument>?,
        val journalfoerteVedlegg: List<JournalfoertDokument>?,
    ) {
        data class Dokument(
            val mellomlagerId: String,
            val name: String,
        )

        data class JournalfoertDokument(
            val kildeJournalpostId: String,
            val dokumentInfoId: String,
        )
    }
}
