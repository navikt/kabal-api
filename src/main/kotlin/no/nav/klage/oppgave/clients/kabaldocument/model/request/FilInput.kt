package no.nav.klage.oppgave.clients.kabaldocument.model.request

import org.springframework.web.multipart.MultipartFile

data class FilInput(
    val file: MultipartFile
)