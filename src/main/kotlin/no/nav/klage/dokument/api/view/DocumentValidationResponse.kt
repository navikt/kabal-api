package no.nav.klage.dokument.api.view

import java.util.*

data class DocumentValidationResponse(
    val dokumentId: UUID,
    val errors: List<SmartDocumentErrorType> = emptyList()
) {
    enum class SmartDocumentErrorType {
        EMPTY_PLACEHOLDER,
        WRONG_DATE,
        DOCUMENT_MODIFIED,
        EMPTY_REGELVERK,
        INVALID_RECIPIENT,
        KLAGEVEDTAK_DATO_NOT_SET,
        FORSTERKET_RETT_NOT_SET,
    }
}