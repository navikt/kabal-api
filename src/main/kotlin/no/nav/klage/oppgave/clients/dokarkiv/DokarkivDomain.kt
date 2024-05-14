package no.nav.klage.oppgave.clients.dokarkiv

data class UpdateDocumentTitlesJournalpostInput(
    val dokumenter: List<UpdateDocumentTitleDokumentInput>,
)

data class UpdateDocumentTitleDokumentInput(
    val dokumentInfoId: String,
    val tittel: String,
)

data class UpdateJournalpostResponse(
    val journalpostId: String,
)

data class LogiskVedleggPayload(
    val tittel: String,
)

data class AddLogiskVedleggResponse(
    val logiskVedleggId: String
)
