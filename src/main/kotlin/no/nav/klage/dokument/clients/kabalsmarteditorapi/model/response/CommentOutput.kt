package no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
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