package no.nav.klage.dokument.clients.kabaljsontopdf.domain

import java.time.LocalDateTime

data class DocumentValidationResponse(
    val errors: List<DocumentValidationError> = emptyList()
) {
    data class DocumentValidationError(
        val type: String,
        val paths: List<List<Int>> = emptyList()
    )
}

data class InnholdsfortegnelseRequest(
    val dokumenterUnderArbeid: List<Document>,
    val journalfoerteDokumenter: List<Document>,
) {
    data class Document(
        val tittel: String,
        val tema: String,
        val opprettet: LocalDateTime,
        val avsenderMottaker: String,
        val saksnummer: String,
        val type: String,
    )
}