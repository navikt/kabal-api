package no.nav.klage.dokument.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.PatchSmartHovedDokumentInput
import no.nav.klage.dokument.api.view.SmartEditorDocumentView
import no.nav.klage.dokument.api.view.SmartHovedDokumentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.KabalSmartEditorApiClient
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.CommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.ModifyCommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.CommentOutput
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.exceptions.ValidationException
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
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
    private val kabalSmartEditorApiClient: KabalSmartEditorApiClient,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val dokumentMapper: DokumentMapper,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val behandlingService: BehandlingService

    ) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @GetMapping
    fun findSmartDokumenter(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): List<SmartEditorDocumentView> {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        return dokumentUnderArbeidService.getSmartDokumenterUnderArbeid(behandlingId = behandlingId, ident = ident)
            .map {
                val smartEditorId =
                    dokumentUnderArbeidService.getSmartEditorId(
                        dokumentId = it.id,
                        readOnly = true
                    )

                val smartEditorDocument = kabalSmartEditorApiClient.getDocument(smartEditorId)
                dokumentMapper.mapToSmartEditorDocumentView(
                    dokumentUnderArbeid = it,
                    smartEditorDocument = smartEditorDocument
                )
            }
    }

    @PostMapping
    fun createSmartHoveddokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody body: SmartHovedDokumentInput,
    ): SmartEditorDocumentView {
        logger.debug("Kall mottatt på createSmartHoveddokument")

        if (body.version != null) {
            throw ValidationException("Du har en gammel versjon av Kabal. Last på nytt. Teknisk: version skal ikke lenger sendes.")
        }

        val dokumentUnderArbeid = dokumentUnderArbeidService.opprettSmartdokument(
            behandlingId = behandlingId,
            dokumentType = if (body.dokumentTypeId != null) DokumentType.of(body.dokumentTypeId) else DokumentType.VEDTAK,
            json = body.content.toString(),
            smartEditorTemplateId = body.templateId ?: error("TODO. Can be null?"),
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            tittel = body.tittel ?: DokumentType.VEDTAK.defaultFilnavn,
            parentId = body.parentId,
        )

        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = dokumentUnderArbeid.id,
                readOnly = false
            )

        val smartEditorDocument = kabalSmartEditorApiClient.getDocument(smartEditorId)

        return dokumentMapper.mapToSmartEditorDocumentView(
            dokumentUnderArbeid = dokumentUnderArbeid,
            smartEditorDocument = smartEditorDocument,
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
    ): SmartEditorDocumentView {

        if (input.version != null) {
            throw ValidationException("Du har en gammel versjon av Kabal. Last på nytt. Teknisk: version skal ikke lenger sendes.")
        }

        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = dokumentId,
                readOnly = false
            )

        dokumentUnderArbeidService.validateDocument(dokumentId = dokumentId)

        if (input.templateId != null) {
            dokumentUnderArbeidService.updateSmartEditorTemplateId(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                templateId = input.templateId,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        }

        val updatedDocument = kabalSmartEditorApiClient.updateDocument(smartEditorId, input.content.toString())

        return dokumentMapper.mapToSmartEditorDocumentView(
            dokumentUnderArbeid = dokumentUnderArbeidService.getDokumentUnderArbeid(dokumentId),
            smartEditorDocument = updatedDocument,
        )
    }

    @Operation(
        summary = "Get document",
        description = "Get document"
    )
    @GetMapping("/{dokumentId}")
    fun getDocument(@PathVariable("dokumentId") documentId: UUID): SmartEditorDocumentView {
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )
        val document = kabalSmartEditorApiClient.getDocument(smartEditorId)

        return dokumentMapper.mapToSmartEditorDocumentView(
            dokumentUnderArbeid = dokumentUnderArbeidService.getDokumentUnderArbeid(documentId),
            smartEditorDocument = document,
        )
    }

    @Operation(
        summary = "Create comment for a given document",
        description = "Create comment for a given document"
    )
    @PostMapping("/{dokumentId}/comments")
    fun createComment(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") documentId: UUID,
        @RequestBody commentInput: CommentInput
    ): CommentOutput {

        dokumentUnderArbeidService.validateDocument(documentId)

        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )

        return kabalSmartEditorApiClient.createComment(smartEditorId, commentInput)
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

        return kabalSmartEditorApiClient.modifyComment(
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
        return kabalSmartEditorApiClient.getAllCommentsWithPossibleThreads(smartEditorId)
    }

    @Operation(
        summary = "Reply to a given comment",
        description = "Reply to a given comment"
    )
    @PostMapping("/{dokumentId}/comments/{commentId}/replies")
    fun replyToComment(
        @PathVariable("behandlingId") behandlingId: UUID,
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

        return kabalSmartEditorApiClient.replyToComment(smartEditorId, commentId, commentInput)
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
        return kabalSmartEditorApiClient.getCommentWithPossibleThread(smartEditorId, commentId)
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

        val behandling = behandlingService.getBehandling(behandlingId)

        kabalSmartEditorApiClient.deleteCommentWithPossibleThread(
            documentId = smartEditorId,
            commentId = commentId,
            behandlingTildeltIdent = behandling.tildeling?.saksbehandlerident
        )
    }
}
