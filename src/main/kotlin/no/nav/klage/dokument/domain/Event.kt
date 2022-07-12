package no.nav.klage.dokument.domain

/**
 * SSE for subscribing clients
 */
data class Event(
    val name: String,
    val id: String,
    val data: String,
)