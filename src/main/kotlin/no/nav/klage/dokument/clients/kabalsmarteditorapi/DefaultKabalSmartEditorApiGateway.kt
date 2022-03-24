package no.nav.klage.dokument.clients.kabalsmarteditorapi

import no.nav.klage.dokument.domain.OpplastetMellomlagretDokument
import no.nav.klage.dokument.domain.SmartEditorDokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentType
import no.nav.klage.oppgave.util.getLogger
import org.apache.tika.Tika
import org.springframework.http.MediaType
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

    fun isMellomlagretDokumentStale(smartEditorId: UUID, sistOpplastet: LocalDateTime): Boolean {
        return kabalSmartEditorApiClient.getDocument(smartEditorId).modified.isAfter(sistOpplastet)
    }

    @Retryable
    fun getDocumentAsPDF(smartEditorId: UUID, documentTitle: String): SmartEditorDokument {
        val responseEntity = kabalSmartEditorApiClient.getDocumentAsPDF(smartEditorId)
        return responseEntity.let {
            SmartEditorDokument(
                smartEditorId = smartEditorId,
                mellomlagretDokument =
                OpplastetMellomlagretDokument(
                    content = it,
                    title = documentTitle,
                    contentType = MediaType.valueOf(Tika().detect(it)) //TODO Fra header?
                )
            )
        }
    }

    fun createDocument(
        json: String,
        dokumentType: DokumentType,
        innloggetIdent: String,
        documentTitle: String,
    ): Pair<SmartEditorDokument, LocalDateTime> {
        val documentOutput = kabalSmartEditorApiClient.createDocument(json)
        return Pair(getDocumentAsPDF(documentOutput.id, documentTitle), documentOutput.modified)
    }

    fun deleteDocument(smartEditorId: UUID) {
        kabalSmartEditorApiClient.deleteDocument(smartEditorId)
    }
}