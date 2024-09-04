package no.nav.klage.dokument.api.controller

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.dokument.api.view.*
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.CommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.ModifyCommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.CommentOutput
import no.nav.klage.dokument.service.SmartDocumentService
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*


@RestController
@Tag(name = "kabal-api-smartdokumenter")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/behandlinger/{behandlingId}/smartdokumenter")
class SmartEditorController(
    private val smartDocumentService: SmartDocumentService
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @PostMapping
    fun createSmartHoveddokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: SmartHovedDokumentInput,
    ): DokumentView {
        logger.debug("Kall mottatt på createSmartHoveddokument")

        return smartDocumentService.createSmartDocument(
            behandlingId = behandlingId,
            input = input,
        )
    }

    @Operation(
        summary = "Update document",
        description = "Update document"
    )
    @PatchMapping("/{dokumentId}")
    fun patchDocument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
        @RequestBody input: PatchSmartHovedDokumentInput,
    ): SmartDocumentModified {
        return smartDocumentService.patchSmartDocument(
            behandlingId = behandlingId,
            dokumentId = dokumentId,
            input = input
        )
    }

    @Operation(
        summary = "Get document",
        description = "Get document"
    )
    @GetMapping("/{dokumentId}")
    fun getDocument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") documentId: UUID
    ): DokumentView {
        return smartDocumentService.getSmartDocument(
            behandlingId = behandlingId,
            documentId = documentId
        )
    }

    @Operation(
        summary = "Get document with specific version",
        description = "Get document with version"
    )
    @GetMapping("/{dokumentId}/versions/{version}")
    fun getDocumentVersion(
        @PathVariable("dokumentId") documentId: UUID,
        @PathVariable("version") version: Int,
    ): JsonNode {
        return smartDocumentService.getSmartDocumentVersion(
            documentId = documentId,
            version = version
        )
    }

    @GetMapping("/{dokumentId}/versions")
    fun findSmartDocumentVersions(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") documentId: UUID,
    ): List<SmartDocumentVersionView> {
        return smartDocumentService.findSmartDocumentVersions(
            behandlingId = behandlingId,
            documentId = documentId
        )
    }

    @Operation(
        summary = "Create comment for a given document",
        description = "Create comment for a given document"
    )
    @PostMapping("/{dokumentId}/comments")
    fun createComment(
        @PathVariable("dokumentId") documentId: UUID,
        @RequestBody commentInput: CommentInput
    ): CommentOutput {
        return smartDocumentService.createComment(
            documentId = documentId,
            commentInput = commentInput
        )
    }

    @Operation(
        summary = "Modify comment for a given document",
        description = "Modify comment for a given document"
    )
    @PatchMapping("/{dokumentId}/comments/{commentId}")
    fun modifyComment(
        @PathVariable("dokumentId") documentId: UUID,
        @PathVariable("commentId") commentId: UUID,
        @RequestBody modifyCommentInput: ModifyCommentInput
    ): CommentOutput {
        return smartDocumentService.modifyComment(
            documentId = documentId,
            commentId = commentId,
            modifyCommentInput = modifyCommentInput
        )
    }

    @Operation(
        summary = "Get all comments for a given document",
        description = "Get all comments for a given document"
    )
    @GetMapping("/{dokumentId}/comments")
    fun getAllCommentsWithPossibleThreads(
        @PathVariable("dokumentId") documentId: UUID
    ): List<CommentOutput> {
        return smartDocumentService.getAllCommentsWithPossibleThreads(
            documentId = documentId
        )
    }

    @Operation(
        summary = "Reply to a given comment",
        description = "Reply to a given comment"
    )
    @PostMapping("/{dokumentId}/comments/{commentId}/replies")
    fun replyToComment(
        @PathVariable("dokumentId") documentId: UUID,
        @PathVariable("commentId") commentId: UUID,
        @RequestBody commentInput: CommentInput,
    ): CommentOutput {
        return smartDocumentService.replyToComment(
            documentId = documentId,
            commentId = commentId,
            commentInput = commentInput
        )
    }

    @Operation(
        summary = "Get a given comment",
        description = "Get a given comment"
    )
    @GetMapping("/{dokumentId}/comments/{commentId}")
    fun getCommentWithPossibleThread(
        @PathVariable("dokumentId") documentId: UUID,
        @PathVariable("commentId") commentId: UUID
    ): CommentOutput {
        return smartDocumentService.getCommentWithPossibleThreads(
            documentId = documentId,
            commentId = commentId,
        )
    }

    @Operation(
        summary = "Delete a given comment (includes possible thread)",
        description = "Delete a given comment (includes possible thread)"
    )
    @DeleteMapping("/{dokumentId}/comments/{commentId}")
    fun deleteCommentWithPossibleThread(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") documentId: UUID,
        @PathVariable("commentId") commentId: UUID
    ) {
        smartDocumentService.deleteCommentWithPossibleThread(
            behandlingId = behandlingId,
            documentId = documentId,
            commentId = commentId,
        )
    }
}
