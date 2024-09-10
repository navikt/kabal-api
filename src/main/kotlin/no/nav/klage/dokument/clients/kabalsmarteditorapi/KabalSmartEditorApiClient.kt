package no.nav.klage.dokument.clients.kabalsmarteditorapi

import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.CommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.DeleteCommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.DocumentUpdateInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.ModifyCommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.CommentOutput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.SmartDocumentResponse
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.SmartDocumentVersionResponse
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.*

@Component
class KabalSmartEditorApiClient(
    private val kabalSmartEditorApiWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun createDocument(
        jsonInput: String,
        data: String?,
    ): SmartDocumentResponse {
        return kabalSmartEditorApiWebClient.post()
            .uri { it.path("/documents").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                DocumentUpdateInput(
                    json = jsonInput,
                    data = data,
                    currentVersion = null,
                )
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::createDocument.name, secureLogger)
            }
            .bodyToMono<SmartDocumentResponse>()
            .block() ?: throw RuntimeException("Document could not be created")
    }

    fun updateDocument(
        documentId: UUID,
        jsonInput: String,
        data: String?,
        currentVersion: Int?,
    ): SmartDocumentResponse {
        return kabalSmartEditorApiWebClient.put()
            .uri { it.path("/documents/$documentId").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                DocumentUpdateInput(
                    json = jsonInput,
                    data = data,
                    currentVersion = currentVersion,
                )
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::updateDocument.name, secureLogger)
            }
            .bodyToMono<SmartDocumentResponse>()
            .block() ?: throw RuntimeException("Document could not be updated")
    }

    fun getDocument(
        documentId: UUID
    ): SmartDocumentResponse {
        return kabalSmartEditorApiWebClient.get()
            .uri { it.path("/documents/$documentId").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getDocument.name, secureLogger)
            }
            .bodyToMono<SmartDocumentResponse>()
            .block() ?: throw RuntimeException("Document could not be retrieved")
    }

    fun getDocumentVersion(
        documentId: UUID,
        version: Int,
    ): SmartDocumentResponse {
        return kabalSmartEditorApiWebClient.get()
            .uri { it.path("/documents/$documentId/versions/$version").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getDocument.name, secureLogger)
            }
            .bodyToMono<SmartDocumentResponse>()
            .block() ?: throw RuntimeException("Document could not be retrieved")
    }

    fun deleteDocument(
        documentId: UUID
    ) {
        kabalSmartEditorApiWebClient.delete()
            .uri { it.path("/documents/$documentId").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::deleteDocument.name, secureLogger)
            }
            .bodyToMono<Unit>()
            .block()
    }

    fun deleteDocumentAsSystemUser(
        documentId: UUID
    ) {
        kabalSmartEditorApiWebClient.delete()
            .uri { it.path("/documents/$documentId").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getAppAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::deleteDocumentAsSystemUser.name, secureLogger)
            }
            .bodyToMono<Unit>()
            .block()
    }

    fun getDocumentVersions(
        documentId: UUID
    ): List<SmartDocumentVersionResponse> {
        return kabalSmartEditorApiWebClient.get()
            .uri { it.path("/documents/$documentId/versions").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getDocument.name, secureLogger)
            }
            .bodyToMono<List<SmartDocumentVersionResponse>>()
            .block() ?: throw RuntimeException("Document versions could not be retrieved")
    }

    fun createComment(
        documentId: UUID,
        input: CommentInput
    ): CommentOutput {
        return kabalSmartEditorApiWebClient.post()
            .uri { it.path("/documents/$documentId/comments").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::createComment.name, secureLogger)
            }
            .bodyToMono<CommentOutput>()
            .block() ?: throw RuntimeException("Comment could not be created")
    }

    fun getAllCommentsWithPossibleThreads(
        documentId: UUID,
    ): List<CommentOutput> {
        return kabalSmartEditorApiWebClient.get()
            .uri { it.path("/documents/$documentId/comments").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getAllCommentsWithPossibleThreads.name, secureLogger)
            }
            .bodyToMono<List<CommentOutput>>()
            .block() ?: throw RuntimeException("Comments could not be retrieved")
    }

    fun replyToComment(
        documentId: UUID,
        commentId: UUID,
        input: CommentInput
    ): CommentOutput {
        return kabalSmartEditorApiWebClient.post()
            .uri { it.path("/documents/$documentId/comments/$commentId/replies").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::replyToComment.name, secureLogger)
            }
            .bodyToMono<CommentOutput>()
            .block() ?: throw RuntimeException("Comment could not be replied to")
    }

    fun getCommentWithPossibleThread(
        documentId: UUID,
        commentId: UUID
    ): CommentOutput {
        return kabalSmartEditorApiWebClient.get()
            .uri { it.path("/documents/$documentId/comments/$commentId").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::getCommentWithPossibleThread.name, secureLogger)
            }
            .bodyToMono<CommentOutput>()
            .block() ?: throw RuntimeException("Comment could not be retrieved")
    }

    fun deleteCommentWithPossibleThread(
        documentId: UUID,
        commentId: UUID,
        behandlingTildeltIdent: String?
    ): CommentOutput {
        return kabalSmartEditorApiWebClient.post()
            .uri { it.path("/documents/$documentId/comments/$commentId/delete").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .bodyValue(
                DeleteCommentInput(
                    behandlingTildeltIdent = behandlingTildeltIdent
                )
            )
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::deleteCommentWithPossibleThread.name, secureLogger)
            }
            .bodyToMono<CommentOutput>()
            .block() ?: throw RuntimeException("Comment could not be deleted")
    }

    fun modifyComment(
        documentId: UUID,
        commentId: UUID,
        input: ModifyCommentInput
    ): CommentOutput {
        return kabalSmartEditorApiWebClient.patch()
            .uri { it.path("/documents/$documentId/comments/$commentId").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(input)
            .retrieve()
            .onStatus(HttpStatusCode::isError) { response ->
                logErrorResponse(response, ::modifyComment.name, secureLogger)
            }
            .bodyToMono<CommentOutput>()
            .block() ?: throw RuntimeException("Comment could not be modified")
    }
}