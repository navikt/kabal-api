package no.nav.klage.dokument.api.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.DokumentView
import no.nav.klage.dokument.api.view.SmartHovedDokumentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.KabalSmartEditorApiClient
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.CommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.CommentOutput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.DocumentOutput
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentId
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*


@RestController
@Api(tags = ["kabal-api-dokumenter"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/behandlinger/{behandlingId}/dokumenter/smart")
class SmartEditorController(
    private val kabalSmartEditorApiClient: KabalSmartEditorApiClient,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val dokumentMapper: DokumentMapper,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerRepository,

    ) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @PostMapping
    fun createSmartHoveddokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody body: SmartHovedDokumentInput,
    ): DokumentView {
        logger.debug("Kall mottatt på createSmartHoveddokument")
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeidService.opprettOgMellomlagreNyttHoveddokument(
                behandlingId = behandlingId,
                dokumentType = if (body.dokumentTypeId != null) DokumentType.of(body.dokumentTypeId) else DokumentType.VEDTAK,
                opplastetFil = null,
                json = if (body.content != null && !body.content.isNull) body.content.toString() else body.json,
                smartEditorTemplateId = body.templateId,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
                tittel = body.tittel ?: DokumentType.VEDTAK.defaultFilnavn,
            )
        )
    }

    @GetMapping
    fun findSmartDokumenter(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): List<DokumentView> {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        return dokumentUnderArbeidService.findSmartDokumenter(behandlingId = behandlingId, ident = ident)
            .map { dokumentMapper.mapToDokumentView(it) }
    }

    @ApiOperation(
        value = "Update document",
        notes = "Update document"
    )
    @PutMapping("/{dokumentId}")
    fun updateDocument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") documentId: UUID,
        @RequestBody jsonInput: JsonNode
    ): DocumentOutput {
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = DokumentId(documentId),
                readOnly = false
            )

        val updatedDocument = kabalSmartEditorApiClient.updateDocument(smartEditorId, jsonInput.toString())
        updatedDocument.content = jacksonObjectMapper().readTree(updatedDocument.json)
        return updatedDocument
    }

    @ApiOperation(
        value = "Get document",
        notes = "Get document"
    )
    @GetMapping("/{dokumentId}")
    fun getDocument(@PathVariable("dokumentId") documentId: UUID): DocumentOutput {
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = DokumentId(documentId),
                readOnly = true
            )
        val document = kabalSmartEditorApiClient.getDocument(smartEditorId)
        document.content = jacksonObjectMapper().readTree(document.json)
        return document
    }

    @ApiOperation(
        value = "Create comment for a given document",
        notes = "Create comment for a given document"
    )
    @PostMapping("/{dokumentId}/comments")
    fun createComment(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") documentId: UUID,
        @RequestBody commentInput: CommentInput
    ): CommentOutput {
        //TODO: Skal hvem som helst få kommentere?
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = DokumentId(documentId),
                readOnly = true
            )

        return kabalSmartEditorApiClient.createcomment(smartEditorId, commentInput)
    }

    @ApiOperation(
        value = "Get all comments for a given document",
        notes = "Get all comments for a given document"
    )
    @GetMapping("/{dokumentId}/comments")
    fun getAllCommentsWithPossibleThreads(
        @PathVariable("dokumentId") documentId: UUID
    ): List<CommentOutput> {
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = DokumentId(documentId),
                readOnly = true
            )
        return kabalSmartEditorApiClient.getAllCommentsWithPossibleThreads(smartEditorId)
    }

    @ApiOperation(
        value = "Reply to a given comment",
        notes = "Reply to a given comment"
    )
    @PostMapping("/{dokumentId}/comments/{commentId}/replies")
    fun replyToComment(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") documentId: UUID,
        @PathVariable("commentId") commentId: UUID,
        @RequestBody commentInput: CommentInput,
    ): CommentOutput {
        //TODO: Skal hvem som helst få kommentere?
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = DokumentId(documentId),
                readOnly = true
            )

        return kabalSmartEditorApiClient.replyToComment(smartEditorId, commentId, commentInput)
    }

    @ApiOperation(
        value = "Get a given comment",
        notes = "Get a given comment"
    )
    @GetMapping("/{dokumentId}/comments/{commentId}")
    fun getCommentWithPossibleThread(
        @PathVariable("dokumentId") documentId: UUID,
        @PathVariable("commentId") commentId: UUID
    ): CommentOutput {
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = DokumentId(documentId),
                readOnly = true
            )
        return kabalSmartEditorApiClient.getCommentWithPossibleThread(smartEditorId, commentId)
    }
}
