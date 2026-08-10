package no.nav.klage.dokument.domain.dokumenterunderarbeid

/**
 * Lifecycle of an uploaded document. The file itself is uploaded directly to Google by the client,
 * and virus scanned and (if needed) converted to PDF by kabal-file-api. Stored and exposed to clients
 * as the enum name, both in JSON and in the data of the `status` events in the confirm stream.
 */
enum class DokumentStatus {
    /** Initial status. The row exists, but the client has not (successfully) uploaded anything yet. */
    UPLOADING,

    /** The upload has been verified to exist in the bucket. */
    UPLOADED,

    /** The file is being scanned for viruses. */
    VIRUS_SCANNING,

    /** The file is being converted to PDF. Only relevant for files that are not already PDF. */
    CONVERTING,

    /** The file is scanned, converted if necessary, and ready for use. */
    DONE,

    /** Terminal failure: the uploaded file contained a virus and has been deleted from the bucket. */
    VIRUS_FOUND,

    /** Terminal failure: the uploaded file could not be turned into a PDF. */
    CONVERSION_FAILED,
    ;

    fun isTerminal(): Boolean = this in TERMINAL

    companion object {
        private val TERMINAL = setOf(DONE, VIRUS_FOUND, CONVERSION_FAILED)
    }
}
