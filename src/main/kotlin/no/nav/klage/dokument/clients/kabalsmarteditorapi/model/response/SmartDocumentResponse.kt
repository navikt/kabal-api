package no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response

import java.time.LocalDateTime
import java.util.*

data class SmartDocumentResponse(
    val documentId: UUID,
    val json: String,
    val data: String?,
    val version: Int,
    val authorNavIdent: String?,
    val created: LocalDateTime,
    val modified: LocalDateTime,
)

data class SmartDocumentVersionResponse(
    val documentId: UUID,
    val version: Int,
    val authorNavIdent: String?,
    val created: LocalDateTime,
    val modified: LocalDateTime,
)