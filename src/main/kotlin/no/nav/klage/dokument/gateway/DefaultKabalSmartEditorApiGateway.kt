package no.nav.klage.dokument.gateway

import no.nav.klage.dokument.api.view.SmartDocumentVersionView
import no.nav.klage.dokument.clients.kabalsmarteditorapi.KabalSmartEditorApiClient
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.CommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.ModifyCommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.CommentOutput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.SmartDocumentResponse
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.UUID

@Service
class DefaultKabalSmartEditorApiGateway(
    private val kabalSmartEditorApiClient: KabalSmartEditorApiClient,
    private val saksbehandlerService: SaksbehandlerService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Retryable(excludes = [WebClientResponseException.NotFound::class])
    fun getDocumentAsJson(smartEditorId: UUID): String = getSmartDocumentResponse(smartEditorId = smartEditorId).json

    @Retryable(excludes = [WebClientResponseException.NotFound::class])
    fun getSmartDocumentResponse(smartEditorId: UUID): SmartDocumentResponse = kabalSmartEditorApiClient.getDocument(smartEditorId)

    @Retryable(excludes = [WebClientResponseException.NotFound::class])
    fun getSmartDocumentResponseForVersion(
        smartEditorId: UUID,
        version: Int,
    ): SmartDocumentResponse = kabalSmartEditorApiClient.getDocumentVersion(documentId = smartEditorId, version = version)

    fun createDocument(
        json: String,
        data: String?,
    ): SmartDocumentResponse = kabalSmartEditorApiClient.createDocument(jsonInput = json, data = data)

    fun deleteDocument(smartEditorId: UUID) {
        kabalSmartEditorApiClient.deleteDocument(smartEditorId)
    }

    fun updateDocument(
        smartDocumentId: UUID,
        json: String,
        data: String?,
        currentVersion: Int?,
    ): SmartDocumentResponse =
        kabalSmartEditorApiClient.updateDocument(
            documentId = smartDocumentId,
            jsonInput = json,
            data = data,
            currentVersion = currentVersion,
        )

    fun getDocumentVersions(documentId: UUID): List<SmartDocumentVersionView> =
        kabalSmartEditorApiClient.getDocumentVersions(documentId = documentId).map {
            SmartDocumentVersionView(
                version = it.version,
                author =
                    it.authorNavIdent?.let { navIdent ->
                        SmartDocumentVersionView.Author(
                            navIdent = navIdent,
                            navn = saksbehandlerService.getNameForIdentDefaultIfNull(navIdent),
                        )
                    },
                timestamp = it.modified,
            )
        }

    fun createComment(
        smartDocumentId: UUID,
        commentInput: CommentInput,
    ): CommentOutput =
        kabalSmartEditorApiClient.createComment(
            documentId = smartDocumentId,
            input = commentInput,
        )

    fun modifyComment(
        documentId: UUID,
        commentId: UUID,
        input: ModifyCommentInput,
    ): CommentOutput =
        kabalSmartEditorApiClient.modifyComment(
            documentId = documentId,
            commentId = commentId,
            input = input,
        )

    fun getAllCommentsWithPossibleThreads(smartEditorId: UUID): List<CommentOutput> =
        kabalSmartEditorApiClient.getAllCommentsWithPossibleThreads(
            documentId = smartEditorId,
        )

    fun replyToComment(
        smartEditorId: UUID,
        commentId: UUID,
        commentInput: CommentInput,
    ): CommentOutput =
        kabalSmartEditorApiClient.replyToComment(
            documentId = smartEditorId,
            commentId = commentId,
            input = commentInput,
        )

    fun getCommentWithPossibleThread(
        smartEditorId: UUID,
        commentId: UUID,
    ): CommentOutput =
        kabalSmartEditorApiClient.getCommentWithPossibleThread(
            documentId = smartEditorId,
            commentId = commentId,
        )

    fun deleteCommentWithPossibleThread(
        documentId: UUID,
        commentId: UUID,
        behandlingTildeltIdent: String?,
    ): CommentOutput =
        kabalSmartEditorApiClient.deleteCommentWithPossibleThread(
            documentId = documentId,
            commentId = commentId,
            behandlingTildeltIdent = behandlingTildeltIdent,
        )
}
