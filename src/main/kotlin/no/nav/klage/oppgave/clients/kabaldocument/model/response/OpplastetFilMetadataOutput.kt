package no.nav.klage.oppgave.clients.kabaldocument.model.response

import java.time.LocalDateTime

data class OpplastetFilMetadataOutput(
    val name: String,
    val size: Long,
    val opplastet: LocalDateTime
)