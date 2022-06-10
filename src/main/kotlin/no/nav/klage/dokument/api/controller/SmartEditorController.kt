package no.nav.klage.dokument.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.PatchSmartHovedDokumentInput
import no.nav.klage.dokument.api.view.SmartEditorDocumentView
import no.nav.klage.dokument.api.view.SmartHovedDokumentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.KabalSmartEditorApiClient
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.CommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.CommentOutput
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
@RequestMapping("/behandlinger/{behandlingId}/smartdokumenter")
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
    ): SmartEditorDocumentView {
        logger.debug("Kall mottatt på createSmartHoveddokument")
        val dokumentUnderArbeid = dokumentUnderArbeidService.opprettSmartdokument(
            behandlingId = behandlingId,
            dokumentType = if (body.dokumentTypeId != null) DokumentType.of(body.dokumentTypeId) else DokumentType.VEDTAK,
            json = body.content.toString(),
            smartEditorTemplateId = body.templateId,
            smartEditorVersion = body.version,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            tittel = body.tittel ?: DokumentType.VEDTAK.defaultFilnavn,
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
                        readOnly = false
                    )

                val smartEditorDocument = kabalSmartEditorApiClient.getDocument(smartEditorId)
                dokumentMapper.mapToSmartEditorDocumentView(
                    dokumentUnderArbeid = it,
                    smartEditorDocument = smartEditorDocument
                )
            }
    }

    @ApiOperation(
        value = "Update document",
        notes = "Update document"
    )
    @PatchMapping("/{dokumentId}")
    fun patchDocument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") documentId: UUID,
        @RequestBody input: PatchSmartHovedDokumentInput,
    ): SmartEditorDocumentView {
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = DokumentId(documentId),
                readOnly = false
            )

        if (input.templateId != null) {
            dokumentUnderArbeidService.updateSmartEditorTemplateId(
                behandlingId = behandlingId,
                dokumentId = DokumentId(id = documentId),
                templateId = input.templateId,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        }

        val dokumentUnderArbeid = dokumentUnderArbeidService.updateSmartEditorVersion(
            behandlingId = behandlingId,
            dokumentId = DokumentId(id = documentId),
            version = input.version,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )

        val updatedDocument = kabalSmartEditorApiClient.updateDocument(smartEditorId, input.content.toString())

        return dokumentMapper.mapToSmartEditorDocumentView(
            dokumentUnderArbeid = dokumentUnderArbeid,
            smartEditorDocument = updatedDocument,
        )
    }

    @ApiOperation(
        value = "Get document",
        notes = "Get document"
    )
    @GetMapping("/{dokumentId}")
    fun getDocument(@PathVariable("dokumentId") documentId: UUID): SmartEditorDocumentView {
        val smartEditorId =
            dokumentUnderArbeidService.getSmartEditorId(
                dokumentId = DokumentId(documentId),
                readOnly = true
            )
        val document = kabalSmartEditorApiClient.getDocument(smartEditorId)

        return dokumentMapper.mapToSmartEditorDocumentView(
            dokumentUnderArbeid = dokumentUnderArbeidService.getDokumentUnderArbeid(DokumentId(documentId)),
            smartEditorDocument = document,
        )
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
