package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.klagefileapi.FileApiClient
import no.nav.klage.oppgave.domain.DokumentInnholdOgTittel
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.http.MediaType
import org.springframework.stereotype.Service

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

    fun getUploadedDocument(mellomlagerId: String): DokumentInnholdOgTittel {
        return DokumentInnholdOgTittel(
            getFileNameFromMellomlagerId(mellomlagerId),
            fileApiClient.getDocument(mellomlagerId),
            standardMediaTypeInGCS
        )
    }

    fun deleteDocument(mellomlagerId: String) {
        fileApiClient.deleteDocument(mellomlagerId)
    }

    fun getUploadedDocumentAsSystemUser(mellomlagerId: String): DokumentInnholdOgTittel {
        return DokumentInnholdOgTittel(
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