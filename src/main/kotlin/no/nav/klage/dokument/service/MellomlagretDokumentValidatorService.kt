package no.nav.klage.dokument.service

import no.nav.klage.dokument.clients.clamav.ClamAvClient
import no.nav.klage.dokument.domain.FysiskDokument
import no.nav.klage.dokument.exceptions.AttachmentHasVirusException
import no.nav.klage.dokument.exceptions.AttachmentIsEmptyException
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service

@Service
class MellomlagretDokumentValidatorService(
    private val clamAvClient: ClamAvClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun validateAttachment(fil: FysiskDokument) {
        logger.debug("Validating attachment.")
        if (fil.content.isEmpty()) {
            logger.warn("Attachment is empty")
            throw AttachmentIsEmptyException()
        }

        if (fil.hasVirus()) {
            logger.warn("Attachment has virus")
            throw AttachmentHasVirusException()
        }

        logger.debug("Validation successful.")
    }

    private fun FysiskDokument.hasVirus() = !clamAvClient.scan(this.content)
}