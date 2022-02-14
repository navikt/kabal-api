package no.nav.klage.dokument.api.view

import org.springframework.web.multipart.MultipartFile

data class FilInput(
    val file: MultipartFile,
    val tittel: String?
)