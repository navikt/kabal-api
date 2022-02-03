package no.nav.klage.oppgave.clients.kabalsmarteditorapi

import brave.Tracer
import no.nav.klage.oppgave.clients.kabalsmarteditorapi.model.request.CommentInput
import no.nav.klage.oppgave.clients.kabalsmarteditorapi.model.response.CommentOutput
import no.nav.klage.oppgave.clients.kabalsmarteditorapi.model.response.DocumentOutput
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.*

@Component
class KabalSmartEditorApiClient(
    private val kabalSmartEditorApiWebClient: WebClient,
    private val tokenUtil: TokenUtil,
    private val tracer: Tracer
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun createDocument(
        jsonInput: String
    ): DocumentOutput {
        return kabalSmartEditorApiWebClient.post()
            .uri { it.path("/documents").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(jsonInput)
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<DocumentOutput>()
            .block() ?: throw RuntimeException("Document could not be created")
    }

    fun updateDocument(
        documentId: UUID,
        jsonInput: String
    ): DocumentOutput {
        return kabalSmartEditorApiWebClient.put()
            .uri { it.path("/documents/$documentId").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(jsonInput)
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<DocumentOutput>()
            .block() ?: throw RuntimeException("Document could not be updated")
    }

    fun getDocument(
        documentId: UUID
    ): DocumentOutput {
        return kabalSmartEditorApiWebClient.get()
            .uri { it.path("/documents/$documentId").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<DocumentOutput>()
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
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<Unit>()
            .block() ?: throw RuntimeException("Document could not be deleted")
    }

    fun createcomment(
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
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
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
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
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
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
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
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<CommentOutput>()
            .block() ?: throw RuntimeException("Comment could not be retrieved")
    }

    fun getDocumentAsPDF(
        documentId: UUID
    ): ResponseEntity<ByteArray> {
        return kabalSmartEditorApiWebClient.get()
            .uri { it.path("/documents/$documentId/pdf").build() }
            .header(
                HttpHeaders.AUTHORIZATION,
                "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithKabalSmartEditorApiScope()}"
            )
            .header("Nav-Call-Id", tracer.currentSpan().context().traceIdString())
            .retrieve()
            .bodyToMono<ResponseEntity<ByteArray>>()
            .block() ?: throw RuntimeException("Document PDF could not be retrieved")
    }
}