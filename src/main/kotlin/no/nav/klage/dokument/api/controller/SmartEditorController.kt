package no.nav.klage.dokument.api.controller

import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.*
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.CommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.ModifyCommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.CommentOutput
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Language
import no.nav.klage.dokument.gateway.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*


@RestController
@Tag(name = "kabal-api-smartdokumenter")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/behandlinger/{behandlingId}/smartdokumenter")
class SmartEditorController(
    private val kabalSmartEditorApiGateway: DefaultKabalSmartEditorApiGateway,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val dokumentMapper: DokumentMapper,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val behandlingService: BehandlingService,
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

        return dokumentUnderArbeidService.opprettSmartdokument(
            behandlingId = behandlingId,
            dokumentType = if (input.dokumentTypeId != null) DokumentType.of(input.dokumentTypeId) else DokumentType.VEDTAK,
            json = input.content.toString(),
            smartEditorTemplateId = input.templateId ?: error("TODO. Can be null?"),
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            tittel = input.tittel ?: DokumentType.VEDTAK.defaultFilnavn,
            parentId = input.parentId,
            language = Language.valueOf(input.language.name),
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
        val smartDocumentId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = dokumentId,
                readOnly = false
            )

        dokumentUnderArbeidService.validateDocument(dokumentId = dokumentId)

        val updatedDocument = kabalSmartEditorApiGateway.updateDocument(
            smartDocumentId = smartDocumentId,
            json = input.content.toString(),
            currentVersion = input.version,
        )

        return SmartDocumentModified(
            modified = updatedDocument.modified,
            version = updatedDocument.version,
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
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )
        val smartEditorDocument = kabalSmartEditorApiGateway.getSmartDocumentResponse(smartEditorId)

        return dokumentUnderArbeidService.getMappedDokumentUnderArbeid(
            dokumentId = documentId,
            smartEditorDocument = smartEditorDocument,
            behandlingId = behandlingId
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
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )
        val document = kabalSmartEditorApiGateway.getSmartDocumentResponseForVersion(
            smartEditorId = smartEditorId,
            version = version,
        )

        return ourJacksonObjectMapper().readTree(document.json)
    }

    @GetMapping("/{dokumentId}/versions")
    fun findSmartDocumentVersions(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") documentId: UUID,
    ): List<SmartDocumentVersionView> {
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )
        return kabalSmartEditorApiGateway.getDocumentVersions(smartEditorId).reversed()
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

        dokumentUnderArbeidService.validateDocument(documentId)

        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )

        return kabalSmartEditorApiGateway.createComment(smartEditorId, commentInput)
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

        dokumentUnderArbeidService.validateDocument(documentId)

        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )

        return kabalSmartEditorApiGateway.modifyComment(
            documentId = smartEditorId,
            commentId = commentId,
            input = modifyCommentInput
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
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )
        return kabalSmartEditorApiGateway.getAllCommentsWithPossibleThreads(smartEditorId)
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
        dokumentUnderArbeidService.validateDocument(documentId)

        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )

        return kabalSmartEditorApiGateway.replyToComment(smartEditorId, commentId, commentInput)
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
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )
        return kabalSmartEditorApiGateway.getCommentWithPossibleThread(smartEditorId, commentId)
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
        dokumentUnderArbeidService.validateDocument(documentId)

        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        kabalSmartEditorApiGateway.deleteCommentWithPossibleThread(
            documentId = smartEditorId,
            commentId = commentId,
            behandlingTildeltIdent = behandling.tildeling?.saksbehandlerident
        )
    }
}
