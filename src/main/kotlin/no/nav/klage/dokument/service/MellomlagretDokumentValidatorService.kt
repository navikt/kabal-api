package no.nav.klage.dokument.service

import no.nav.klage.dokument.clients.clamav.ClamAvClient
import no.nav.klage.dokument.exceptions.AttachmentHasVirusException
import no.nav.klage.dokument.exceptions.AttachmentIsEmptyException
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.io.File

@Service
class MellomlagretDokumentValidatorService(
    private val clamAvClient: ClamAvClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun validateAttachment(file: File) {
        logger.debug("Validating attachment.")
        if (file.length() == 0L) {
            logger.warn("Attachment is empty")
            throw AttachmentIsEmptyException()
        }

        if (clamAvClient.hasVirus(file)) {
            logger.warn("Attachment has virus")
            throw AttachmentHasVirusException()
        }

        logger.debug("Validation successful.")
    }

}