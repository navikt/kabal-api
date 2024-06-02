package no.nav.klage.dokument.service


import no.nav.klage.dokument.clients.klagefileapi.FileApiClient
import no.nav.klage.oppgave.util.Image2PDF
import no.nav.klage.oppgave.util.getLogger
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.io.File

@Service
class MellomlagerService(
    private val fileApiClient: FileApiClient,
    private val image2PDF: Image2PDF,
    private val attachmentValidator: MellomlagretDokumentValidatorService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun uploadFile(file: File): String {
        var start = System.currentTimeMillis()
        attachmentValidator.validateAttachment(file)
        logger.debug("Attachment validation took ${System.currentTimeMillis() - start} ms")

        return fileApiClient.uploadDocument(
            //If uploaded file is an image, convert to pdf
            resource = image2PDF.convertIfImage(file),
        )
    }

    fun uploadResource(resource: Resource): String =
        fileApiClient.uploadDocument(
            resource = resource,
        )

    fun getUploadedDocument(mellomlagerId: String): Resource {
        return fileApiClient.getDocument(mellomlagerId)
    }

    fun getUploadedDocumentAsSignedURL(mellomlagerId: String): String {
        return fileApiClient.getDocumentAsSignedURL(mellomlagerId)
    }

    fun deleteDocument(mellomlagerId: String): Unit =
        fileApiClient.deleteDocument(mellomlagerId)

}