package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import no.nav.klage.oppgave.clients.kabalsmarteditorapi.KabalSmartEditorApiClient
import no.nav.klage.oppgave.clients.kabalsmarteditorapi.model.request.CommentInput
import no.nav.klage.oppgave.clients.kabalsmarteditorapi.model.response.CommentOutput
import no.nav.klage.oppgave.clients.kabalsmarteditorapi.model.response.DocumentOutput
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*


@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/smarteditor/documents")
class SmartEditorController(
    private val kabalSmartEditorApiClient: KabalSmartEditorApiClient
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @ApiOperation(
        value = "Create document",
        notes = "Create document"
    )
    @PostMapping
    fun createDocument(
        @RequestBody jsonInput: String
    ): DocumentOutput {
        return kabalSmartEditorApiClient.createDocument(jsonInput)
    }

    @ApiOperation(
        value = "Update document",
        notes = "Update document"
    )
    @PutMapping("/{documentId}")
    fun updateDocument(
        @PathVariable("documentId") documentId: UUID,
        @RequestBody jsonInput: String
    ): DocumentOutput {
        return kabalSmartEditorApiClient.updateDocument(documentId, jsonInput)
    }

    @ApiOperation(
        value = "Get document",
        notes = "Get document"
    )
    @GetMapping("/{documentId}")
    fun getDocument(@PathVariable("documentId") documentId: UUID): DocumentOutput {
        return kabalSmartEditorApiClient.getDocument(documentId)
    }

    @ApiOperation(
        value = "Delete document",
        notes = "Delete document"
    )
    @DeleteMapping("/{documentId}")
    fun deleteDocument(@PathVariable("documentId") documentId: UUID) {
        return kabalSmartEditorApiClient.deleteDocument(documentId)

    }

    @ApiOperation(
        value = "Create comment for a given document",
        notes = "Create comment for a given document"
    )
    @PostMapping("/{documentId}/comments")
    fun createComment(
        @PathVariable("documentId") documentId: UUID,
        @RequestBody commentInput: CommentInput
    ): CommentOutput {
        return kabalSmartEditorApiClient.createcomment(documentId, commentInput)
    }

    @ApiOperation(
        value = "Get all comments for a given document",
        notes = "Get all comments for a given document"
    )
    @GetMapping("/{documentId}/comments")
    fun getAllCommentsWithPossibleThreads(
        @PathVariable("documentId") documentId: UUID
    ): List<CommentOutput> {
        return kabalSmartEditorApiClient.getAllCommentsWithPossibleThreads(documentId)
    }

    @ApiOperation(
        value = "Reply to a given comment",
        notes = "Reply to a given comment"
    )
    @PostMapping("/{documentId}/comments/{commentId}/replies")
    fun replyToComment(
        @PathVariable("documentId") documentId: UUID,
        @PathVariable("commentId") commentId: UUID,
        @RequestBody commentInput: CommentInput,
    ): CommentOutput {
        return kabalSmartEditorApiClient.replyToComment(documentId, commentId, commentInput)
    }

    @ApiOperation(
        value = "Get a given comment",
        notes = "Get a given comment"
    )
    @GetMapping("/{documentId}/comments/{commentId}")
    fun getCommentWithPossibleThread(
        @PathVariable("documentId") documentId: UUID,
        @PathVariable("commentId") commentId: UUID
    ): CommentOutput {
        return kabalSmartEditorApiClient.getCommentWithPossibleThread(documentId, commentId)
    }

    @ApiOperation(
        value = "Generate PDF",
        notes = "Generate PDF"
    )
    @ResponseBody
    @GetMapping("/{documentId}/pdf")
    fun getDocumentAsPDF(
        @PathVariable("documentId") documentId: UUID
    ): ResponseEntity<ByteArray> {
        return kabalSmartEditorApiClient.getDocumentAsPDF(documentId)
    }
}