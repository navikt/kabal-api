package no.nav.klage.dokument.domain

import org.springframework.core.io.Resource
import org.springframework.http.MediaType

/**
 * Not used when uploading documents atm.
 */
data class FysiskDokument(
    val title: String,
    val content: Resource,
    val contentType: MediaType,
)