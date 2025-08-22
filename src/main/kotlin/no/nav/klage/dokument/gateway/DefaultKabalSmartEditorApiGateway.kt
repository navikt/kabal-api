package no.nav.klage.dokument.gateway

import no.nav.klage.dokument.api.view.SmartDocumentVersionView
import no.nav.klage.dokument.clients.kabalsmarteditorapi.KabalSmartEditorApiClient
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.CommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.ModifyCommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.CommentOutput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.SmartDocumentResponse
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import java.util.*

@Service
class DefaultKabalSmartEditorApiGateway(
    private val kabalSmartEditorApiClient: KabalSmartEditorApiClient,
    private val saksbehandlerService: SaksbehandlerService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Retryable
    fun getDocumentAsJson(smartEditorId: UUID): String {
        return getSmartDocumentResponse(smartEditorId = smartEditorId).json
    }

    @Retryable
    fun getSmartDocumentResponse(smartEditorId: UUID): SmartDocumentResponse {
        return kabalSmartEditorApiClient.getDocument(smartEditorId)
    }

    @Retryable
    fun getSmartDocumentResponseForVersion(smartEditorId: UUID, version: Int): SmartDocumentResponse {
        return kabalSmartEditorApiClient.getDocumentVersion(documentId = smartEditorId, version = version)
    }

    fun createDocument(
        json: String,
        data: String?,
    ): SmartDocumentResponse {
        return kabalSmartEditorApiClient.createDocument(jsonInput = json, data = data)
    }

    fun deleteDocument(smartEditorId: UUID) {
        kabalSmartEditorApiClient.deleteDocument(smartEditorId)
    }

    fun deleteDocumentAsSystemUser(smartEditorId: UUID) {
        kabalSmartEditorApiClient.deleteDocumentAsSystemUser(smartEditorId)
    }

    fun updateDocument(smartDocumentId: UUID, json: String, data: String?, currentVersion: Int?): SmartDocumentResponse {
        return kabalSmartEditorApiClient.updateDocument(
            documentId = smartDocumentId,
            jsonInput = json,
            data = data,
            currentVersion = currentVersion,
        )
    }

    fun getDocumentVersions(documentId: UUID): List<SmartDocumentVersionView> {
        return kabalSmartEditorApiClient.getDocumentVersions(documentId = documentId).map {
            SmartDocumentVersionView(
                version = it.version,
                author = it.authorNavIdent?.let { navIdent ->
                    SmartDocumentVersionView.Author(
                        navIdent = navIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(navIdent),
                    )
                },
                timestamp = it.modified,
            )
        }
    }

    fun createComment(smartDocumentId: UUID, commentInput: CommentInput): CommentOutput {
        return kabalSmartEditorApiClient.createComment(
            documentId = smartDocumentId,
            input = commentInput,
        )
    }

    fun modifyComment(documentId: UUID, commentId: UUID, input: ModifyCommentInput): CommentOutput {
        return kabalSmartEditorApiClient.modifyComment(
            documentId = documentId,
            commentId = commentId,
            input = input,
        )
    }

    fun getAllCommentsWithPossibleThreads(smartEditorId: UUID): List<CommentOutput> {
        return kabalSmartEditorApiClient.getAllCommentsWithPossibleThreads(
            documentId = smartEditorId,
        )
    }

    fun replyToComment(smartEditorId: UUID, commentId: UUID, commentInput: CommentInput): CommentOutput {
        return kabalSmartEditorApiClient.replyToComment(
            documentId = smartEditorId,
            commentId = commentId,
            input = commentInput,
        )
    }

    fun getCommentWithPossibleThread(smartEditorId: UUID, commentId: UUID): CommentOutput {
        return kabalSmartEditorApiClient.getCommentWithPossibleThread(
            documentId = smartEditorId,
            commentId = commentId,
        )
    }

    fun deleteCommentWithPossibleThread(documentId: UUID, commentId: UUID, behandlingTildeltIdent: String?): CommentOutput {
        return kabalSmartEditorApiClient.deleteCommentWithPossibleThread(
            documentId = documentId,
            commentId = commentId,
            behandlingTildeltIdent = behandlingTildeltIdent,
        )
    }

}