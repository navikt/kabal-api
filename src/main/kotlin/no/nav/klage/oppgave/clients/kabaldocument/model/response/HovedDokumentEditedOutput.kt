package no.nav.klage.oppgave.clients.kabaldocument.model.response

import java.time.LocalDateTime

data class HovedDokumentEditedOutput(
    val modified: LocalDateTime,
    val fileMetadata: OpplastetFilMetadataOutput?
)
