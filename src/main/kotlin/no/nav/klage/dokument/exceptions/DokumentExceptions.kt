package no.nav.klage.dokument.exceptions

import no.nav.klage.dokument.api.view.DocumentValidationResponse

class DokumentValidationException(msg: String) : RuntimeException(msg)

class AddressValidationException(msg: String) : RuntimeException(msg)

class SmartDocumentValidationException(msg: String, val errors: List<DocumentValidationResponse>) : RuntimeException(msg)

class SvarbrevPreviewException(msg: String) : RuntimeException(msg)

class DocumentDoesNotExistException(msg: String) : RuntimeException(msg)

class NoAccessToDocumentException(msg: String) : RuntimeException(msg)