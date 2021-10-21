package no.nav.klage.oppgave.domain

import org.springframework.http.MediaType

data class DokumentInnhold(
    val content: ByteArray,
    val contentType: MediaType
)
