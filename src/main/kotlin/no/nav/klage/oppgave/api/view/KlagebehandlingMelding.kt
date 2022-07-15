package no.nav.klage.oppgave.api.view

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import java.util.*

data class MeldingInput(
    val text: String
)

data class MeldingModified(
    val modified: LocalDateTime
)

data class MeldingView(
    val id: UUID,
    val text: String,
    val author: Author,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    val created: LocalDateTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DATE_FORMAT)
    val modified: LocalDateTime?
) {

    companion object {
        const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"
    }

    data class Author(
        val saksbehandlerIdent: String,
        val name: String
    )
}