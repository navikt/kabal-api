package no.nav.klage.dokument.clients.kabalsmarteditorapi

import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.util.getLogger
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
class DefaultKabalSmartEditorApiGateway(private val kabalSmartEditorApiClient: KabalSmartEditorApiClient) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun isMellomlagretDokumentStale(
        smartEditorId: UUID,
        mellomlagretDate: LocalDateTime?
    ): Boolean {
        return mellomlagretDate == null || kabalSmartEditorApiClient.getDocument(smartEditorId).modified.isAfter(
            mellomlagretDate
        )
    }

    @Retryable
    fun getDocumentAsJson(smartEditorId: UUID): String {
        return kabalSmartEditorApiClient.getDocument(smartEditorId).json!!
    }

    fun createDocument(
        json: String,
        dokumentType: DokumentType,
        innloggetIdent: String,
        documentTitle: String,
    ): UUID {
        return kabalSmartEditorApiClient.createDocument(json).id
    }

    fun deleteDocument(smartEditorId: UUID) {
        kabalSmartEditorApiClient.deleteDocument(smartEditorId)
    }

    fun deleteDocumentAsSystemUser(smartEditorId: UUID) {
        kabalSmartEditorApiClient.deleteDocumentAsSystemUser(smartEditorId)
    }
}