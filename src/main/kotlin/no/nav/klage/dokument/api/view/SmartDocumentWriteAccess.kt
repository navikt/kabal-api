package no.nav.klage.dokument.api.view

import java.util.*

data class SmartDocumentsWriteAccessList(
    val smartDocumentWriteAccessList: List<SmartDocumentWriteAccess>,
)

data class SmartDocumentWriteAccess(
    val documentId: UUID,
    val navIdents: String,
)