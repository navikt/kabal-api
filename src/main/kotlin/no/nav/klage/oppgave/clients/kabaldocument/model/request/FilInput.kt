package no.nav.klage.dokument.api.input

import org.springframework.web.multipart.MultipartFile

data class FilInput(
    val file: MultipartFile
)