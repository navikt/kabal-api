package no.nav.klage.dokument.api.view

import java.time.LocalDateTime
import java.util.*

data class SmartDocumentVersionView(
    val id: UUID,
    val version: Int,
    val author: Author?,
    val timestamp: LocalDateTime,
) {
    data class Author(
        val navIdent: String,
        val navn: String,
    )
}
