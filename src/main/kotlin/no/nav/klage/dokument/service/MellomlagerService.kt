package no.nav.klage.dokument.service


import no.nav.klage.dokument.clients.klagefileapi.FileApiClient
import no.nav.klage.dokument.clients.klagefileapi.UploadPostPolicyResponse
import no.nav.klage.oppgave.util.getLogger
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import java.io.File

@Service
class MellomlagerService(
    private val fileApiClient: FileApiClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun uploadFile(
        file: File,
        systemContext: Boolean,
    ): String =
        fileApiClient.uploadDocument(
            resource = FileSystemResource(file),
            systemUser = systemContext,
        )

    fun uploadResource(resource: Resource): String =
        fileApiClient.uploadDocument(
            resource = resource,
        )

    fun createUploadPolicies(contentTypes: List<String>): List<UploadPostPolicyResponse> =
        fileApiClient.createUploadPolicies(contentTypes = contentTypes)

    fun getUploadedDocument(mellomlagerId: String): Resource {
        return fileApiClient.getDocument(mellomlagerId)
    }

    fun getUploadedDocumentAsSignedURL(
        mellomlagerId: String,
        filename: String,
        contentDisposition: String,
    ): String {
        return fileApiClient.getDocumentAsSignedURL(
            id = mellomlagerId,
            filename = filename,
            contentDisposition = contentDisposition,
        )
    }

    fun deleteDocument(mellomlagerId: String, systemContext: Boolean = false): Unit =
        fileApiClient.deleteDocument(mellomlagerId, systemContext)

}
