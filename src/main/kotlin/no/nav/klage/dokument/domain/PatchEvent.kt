package no.nav.klage.dokument.domain

import java.util.*

data class PatchEvent(
    val documentId: UUID,
    val json: String,
)