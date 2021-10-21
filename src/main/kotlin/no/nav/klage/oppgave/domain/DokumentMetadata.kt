package no.nav.klage.oppgave.domain

import java.time.LocalDateTime

data class DokumentMetadata(
    val title: String,
    val size: Long,
    val opplastet: LocalDateTime
)
