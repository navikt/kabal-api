package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.klagefileapi.FileApiClient
import no.nav.klage.oppgave.domain.ArkivertDokumentWithTitle
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class FileApiService(
    private val fileApiClient: FileApiClient
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        private val standardMediaTypeInGCS = MediaType.valueOf("application/pdf")

    }

    fun uploadDocument(file: MultipartFile): String {
        return fileApiClient.uploadDocument(file.bytes, file.originalFilename)
    }

    fun getUploadedDocument(mellomlagerId: String): ArkivertDokumentWithTitle {
        return ArkivertDokumentWithTitle(
            getFileNameFromMellomlagerId(mellomlagerId),
            fileApiClient.getDocument(mellomlagerId),
            standardMediaTypeInGCS
        )
    }

    fun deleteDocument(mellomlagerId: String) {
        fileApiClient.deleteDocument(mellomlagerId)
    }

    fun getUploadedDocumentAsSystemUser(mellomlagerId: String): ArkivertDokumentWithTitle {
        return ArkivertDokumentWithTitle(
            getFileNameFromMellomlagerId(mellomlagerId),
            fileApiClient.getDocument(mellomlagerId, true),
            standardMediaTypeInGCS
        )
    }

    fun deleteDocumentAsSystemUser(mellomlagerId: String) {
        fileApiClient.deleteDocument(mellomlagerId, true)
    }



    private fun getFileNameFromMellomlagerId(mellomlagerId: String): String = mellomlagerId.substring(36)
}