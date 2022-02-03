package no.nav.klage.oppgave.clients.kabalsmarteditorapi.model.request

data class CommentInput(
    val text: String,
    val author: Author
) {
    data class Author(
        val name: String,
        val ident: String
    )
}
