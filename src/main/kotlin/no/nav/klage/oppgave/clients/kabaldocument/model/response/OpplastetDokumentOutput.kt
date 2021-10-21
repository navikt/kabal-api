package no.nav.klage.oppgave.clients.kabaldocument.model.response

import java.time.LocalDateTime

data class OpplastetDokumentOutput(
    val id: String,
    val mellomlagerId: String,
    val opplastet: LocalDateTime,
    val size: Long,
    val name: String
)


