package no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response

import java.time.LocalDateTime
import java.util.*

data class CommentOutput(
    val id: UUID,
    val text: String,
    val author: Author,
    val comments: List<CommentOutput> = emptyList(),
    val created: LocalDateTime,
    val modified: LocalDateTime,
    val parentId: UUID?,
) {
    data class Author(
        val name: String,
        val ident: String
    )
}