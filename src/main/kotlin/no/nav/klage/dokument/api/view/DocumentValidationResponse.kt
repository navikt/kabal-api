package no.nav.klage.dokument.api.view

import java.util.*

data class DocumentValidationResponse(
    val dokumentId: UUID,
    val errors: List<DocumentValidationError> = emptyList()
) {
    data class DocumentValidationError(
        val type: SmartDocumentErrorType,
    ) {
        enum class SmartDocumentErrorType {
            EMPTY_PLACEHOLDER,
            WRONG_DATE,
            DOCUMENT_MODIFIED,
        }
    }
}