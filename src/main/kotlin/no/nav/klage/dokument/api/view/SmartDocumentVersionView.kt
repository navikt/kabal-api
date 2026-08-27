package no.nav.klage.dokument.api.view

import java.time.LocalDateTime

data class SmartDocumentVersionView(
    val version: Int,
    val author: Author?,
    val timestamp: LocalDateTime,
) {
    data class Author(
        val navIdent: String,
        val navn: String,
    )
}
