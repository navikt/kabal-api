package no.nav.klage.dokument.exceptions

class AttachmentTooLargeException(override val message: String = "TOO_LARGE") : RuntimeException() {
    @Synchronized
    fun fillInStackTrace(): Throwable {
        //Remove stacktrace
        return this
    }
}

class AttachmentIsEmptyException(override val message: String = "EMPTY") : RuntimeException()
class AttachmentHasVirusException(override val message: String = "VIRUS") : RuntimeException()
