package no.nav.klage.dokument.exceptions

class AttachmentTooLargeException(override val message: String = "TOO_LARGE") : RuntimeException() {
    @Synchronized
    fun fillInStackTrace(): Throwable {
        //Remove stacktrace
        return this
    }
}

/**
 * The document in kabal-file-api is not the one we scanned, typically because someone else converted
 * it in the meantime. Whoever gets this has to scan the current version before converting it.
 */
class ConversionConflictException(override val message: String = "CONVERSION_CONFLICT") : RuntimeException()
