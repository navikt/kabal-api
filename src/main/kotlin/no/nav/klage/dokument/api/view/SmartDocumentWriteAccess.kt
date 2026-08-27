package no.nav.klage.dokument.api.view

import java.util.UUID

data class SmartDocumentsWriteAccessList(
    val smartDocumentWriteAccessList: List<SmartDocumentWriteAccess>,
)

data class SmartDocumentWriteAccess(
    val documentId: UUID,
    val navIdents: List<String>,
)
