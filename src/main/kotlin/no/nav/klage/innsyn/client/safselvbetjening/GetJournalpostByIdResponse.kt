package no.nav.klage.innsyn.client.safselvbetjening

data class GetJournalpostByIdResponse(val data: GetJournalpostById?, val errors: List<PdlError>?)

data class GetJournalpostById(val journalpostById: JournalpostById?)

data class JournalpostById(
    val journalpostId: String,
    val tittel: String,
    val dokumenter: List<Dokument>,
)

data class Dokument(
    val dokumentInfoId: String,
)

data class PdlError(
    val message: String,
    val locations: List<PdlErrorLocation>,
    val path: List<String>?,
    val extensions: PdlErrorExtension
)

data class PdlErrorLocation(
    val line: Int?,
    val column: Int?
)

data class PdlErrorExtension(
    val code: String?,
    val classification: String
)