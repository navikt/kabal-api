package no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request

data class DocumentUpdateInput(
    val json: String,
    val data: String?,
    val currentVersion: Int?,
)