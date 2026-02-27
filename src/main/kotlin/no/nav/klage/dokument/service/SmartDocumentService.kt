package no.nav.klage.dokument.service

import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.*
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.CommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.ModifyCommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.CommentOutput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.SmartDocumentResponse
import no.nav.klage.dokument.domain.SmartDocumentAccessBehandlingEvent
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsSmartdokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Language
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.gateway.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.repositories.SmartdokumentUnderArbeidAsHoveddokumentRepository
import no.nav.klage.dokument.repositories.SmartdokumentUnderArbeidAsVedleggRepository
import no.nav.klage.dokument.util.DuaAccessPolicy
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.KafkaInternalEventService
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.util.*
import kotlin.time.measureTime

@Service
@Transactional
class SmartDocumentService(
    private val kabalSmartEditorApiGateway: DefaultKabalSmartEditorApiGateway,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val smartDokumentUnderArbeidAsHoveddokumentRepository: SmartdokumentUnderArbeidAsHoveddokumentRepository,
    private val smartDokumentUnderArbeidAsVedleggRepository: SmartdokumentUnderArbeidAsVedleggRepository,
    private val behandlingService: BehandlingService,
    private val dokumentMapper: DokumentMapper,
    private val saksbehandlerService: SaksbehandlerService,
    private val documentPolicyService: DocumentPolicyService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val jacksonObjectMapper = jacksonObjectMapper()
    }

    fun createSmartDocument(
        behandlingId: UUID,
        input: SmartHovedDokumentInput,
    ): DokumentView {
        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        val parentId = input.parentId
        val tittel = input.tittel ?: DokumentType.VEDTAK.defaultFilnavn
        val dokumentType =
            if (input.dokumentTypeId != null) DokumentType.of(input.dokumentTypeId) else DokumentType.VEDTAK
        val language = Language.valueOf(input.language.name)
        val smartEditorTemplateId = input.templateId

        val behandling = behandlingService.getBehandlingAndCheckReadAccessToSak(behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        val duration = measureTime {
            documentPolicyService.validateDokumentUnderArbeidAction(
                behandling = behandling,
                dokumentType = when (smartEditorTemplateId) {
                    "rol-questions" -> DuaAccessPolicy.DokumentType.ROL_QUESTIONS
                    "rol-answers" -> DuaAccessPolicy.DokumentType.ROL_ANSWERS
                    else -> DuaAccessPolicy.DokumentType.SMART_DOCUMENT
                },
                parentDokumentType = documentPolicyService.getParentDokumentType(parentDuaId = parentId),
                documentRole = behandlingRole,
                action = DuaAccessPolicy.Action.CREATE,
                duaMarkertFerdig = false,
            )
        }
        logger.debug("Validation for creating smart document took ${duration.inWholeMilliseconds} ms")

        val smartDocumentResponse =
            kabalSmartEditorApiGateway.createDocument(
                json = input.content.toString(),
                data = input.data,
            )

        val now = LocalDateTime.now()

        val document = if (parentId == null) {
            smartDokumentUnderArbeidAsHoveddokumentRepository.save(
                SmartdokumentUnderArbeidAsHoveddokument(
                    mellomlagerId = null,
                    mellomlagretDate = null,
                    size = null,
                    name = tittel,
                    dokumentType = dokumentType,
                    behandlingId = behandlingId,
                    smartEditorId = smartDocumentResponse.documentId,
                    smartEditorTemplateId = smartEditorTemplateId,
                    creatorIdent = innloggetIdent,
                    creatorRole = behandlingRole,
                    created = now,
                    modified = now,
                    journalfoerendeEnhetId = null,
                    language = language,
                    mellomlagretVersion = null,
                )
            )
        } else {
            smartDokumentUnderArbeidAsVedleggRepository.save(
                SmartdokumentUnderArbeidAsVedlegg(
                    mellomlagerId = null,
                    mellomlagretDate = null,
                    size = null,
                    name = tittel,
                    behandlingId = behandlingId,
                    smartEditorId = smartDocumentResponse.documentId,
                    smartEditorTemplateId = smartEditorTemplateId,
                    creatorIdent = innloggetIdent,
                    creatorRole = behandlingRole,
                    parentId = parentId,
                    language = language,
                    created = now,
                    modified = now,
                    mellomlagretVersion = null,
                )
            )
        }

        val smartEditorDocument =
            kabalSmartEditorApiGateway.getSmartDocumentResponse(smartEditorId = document.smartEditorId)

        val dokumentView = dokumentMapper.mapToDokumentView(
            dokumentUnderArbeid = document,
            journalpost = null,
            smartEditorDocument = smartEditorDocument,
            behandling = behandling,
        )

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                DocumentsAddedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    documents = listOf(
                        dokumentView
                    ),
                    traceId = currentTraceId(),
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.DOCUMENTS_ADDED,
        )

        applicationEventPublisher.publishEvent(SmartDocumentAccessBehandlingEvent(behandling))

        return dokumentView
    }

    fun patchSmartDocument(
        behandlingId: UUID,
        dokumentId: UUID,
        input: PatchSmartHovedDokumentInput
    ): SmartDocumentModified {
        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()

        val smartDocumentId =
            getSmartEditorId(
                dokumentId = dokumentId,
                readOnly = false
            )

        //no check for access, since BFF already checks, and it can be a delayed patch call.

        val updatedDocument = kabalSmartEditorApiGateway.updateDocument(
            smartDocumentId = smartDocumentId,
            json = input.content.toString(),
            data = input.data,
            currentVersion = input.version,
        )

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                DocumentPatched(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    author = Employee(
                        navIdent = updatedDocument.authorNavIdent!!,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(updatedDocument.authorNavIdent),
                    ),
                    version = updatedDocument.version,
                    documentId = dokumentId.toString(),
                    traceId = currentTraceId(),
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.SMART_DOCUMENT_VERSIONED,
        )

        return SmartDocumentModified(
            modified = updatedDocument.modified,
            version = updatedDocument.version,
        )
    }

    fun getSmartDocument(
        behandlingId: UUID,
        documentId: UUID,
    ): DokumentView {
        val smartEditorId =
            getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )
        val smartEditorDocument = kabalSmartEditorApiGateway.getSmartDocumentResponse(smartEditorId)

        return getMappedDokumentUnderArbeid(
            dokumentId = documentId,
            smartEditorDocument = smartEditorDocument,
            behandlingId = behandlingId
        )
    }

    fun getSmartDocumentVersion(
        documentId: UUID,
        version: Int,
    ): JsonNode {
        val smartEditorId =
            getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )
        val document = kabalSmartEditorApiGateway.getSmartDocumentResponseForVersion(
            smartEditorId = smartEditorId,
            version = version,
        )

        return jacksonObjectMapper().readTree(document.json)
    }

    fun findSmartDocumentVersions(
        behandlingId: UUID,
        documentId: UUID,
    ): List<SmartDocumentVersionView> {
        val smartEditorId =
            getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )
        return kabalSmartEditorApiGateway.getDocumentVersions(smartEditorId).reversed()
    }

    fun createComment(
        documentId: UUID,
        commentInput: CommentInput,
    ): CommentOutput {
        val document = dokumentUnderArbeidService.getDokumentUnderArbeid(documentId)

        dokumentUnderArbeidService.validateWriteAccessToSmartDocument(
            dokumentId = documentId,
            behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(document.behandlingId),
        )

        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()

        val smartEditorId =
            getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )

        val commentOutput = kabalSmartEditorApiGateway.createComment(smartEditorId, commentInput)

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                CommentEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    author = Employee(
                        navIdent = commentOutput.author.ident,
                        navn = commentOutput.author.name,
                    ),
                    commentId = commentOutput.id.toString(),
                    text = commentOutput.text,
                    documentId = documentId.toString(),
                    parentId = commentOutput.parentId?.toString(),
                    traceId = currentTraceId(),
                )
            ),
            behandlingId = document.behandlingId,
            type = InternalEventType.SMART_DOCUMENT_COMMENT_ADDED,
        )

        return commentOutput
    }

    fun modifyComment(
        documentId: UUID,
        commentId: UUID,
        modifyCommentInput: ModifyCommentInput,
    ): CommentOutput {
        val document = dokumentUnderArbeidService.getDokumentUnderArbeid(documentId)

        dokumentUnderArbeidService.validateWriteAccessToSmartDocument(
            dokumentId = documentId,
            behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(document.behandlingId),
        )

        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()

        val smartEditorId =
            getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )

        val commentOutput = kabalSmartEditorApiGateway.modifyComment(
            documentId = smartEditorId,
            commentId = commentId,
            input = modifyCommentInput
        )

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                CommentEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    author = Employee(
                        navIdent = commentOutput.author.ident,
                        navn = commentOutput.author.name,
                    ),
                    commentId = commentOutput.id.toString(),
                    text = commentOutput.text,
                    documentId = documentId.toString(),
                    parentId = commentOutput.parentId?.toString(),
                    traceId = currentTraceId(),
                )
            ),
            behandlingId = document.behandlingId,
            type = InternalEventType.SMART_DOCUMENT_COMMENT_CHANGED,
        )

        return commentOutput
    }

    fun getAllCommentsWithPossibleThreads(
        documentId: UUID,
    ): List<CommentOutput> {
        val smartEditorId =
            getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )
        return kabalSmartEditorApiGateway.getAllCommentsWithPossibleThreads(smartEditorId = smartEditorId)
    }

    fun replyToComment(
        documentId: UUID,
        commentId: UUID,
        commentInput: CommentInput,
    ): CommentOutput {
        val document = dokumentUnderArbeidService.getDokumentUnderArbeid(documentId)

        dokumentUnderArbeidService.validateWriteAccessToSmartDocument(
            dokumentId = documentId,
            behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(document.behandlingId),
        )

        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()

        val smartEditorId =
            getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )

        val commentOutput = kabalSmartEditorApiGateway.replyToComment(
            smartEditorId = smartEditorId,
            commentId = commentId,
            commentInput = commentInput
        )

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                CommentEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    author = Employee(
                        navIdent = commentOutput.author.ident,
                        navn = commentOutput.author.name,
                    ),
                    commentId = commentOutput.id.toString(),
                    text = commentOutput.text,
                    parentId = commentOutput.parentId?.toString(),
                    documentId = documentId.toString(),
                    traceId = currentTraceId(),
                )
            ),
            behandlingId = document.behandlingId,
            type = InternalEventType.SMART_DOCUMENT_COMMENT_ADDED,
        )

        return commentOutput
    }

    fun getCommentWithPossibleThreads(
        documentId: UUID,
        commentId: UUID,
    ): CommentOutput {
        val smartEditorId =
            getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )
        return kabalSmartEditorApiGateway.getCommentWithPossibleThread(smartEditorId, commentId)
    }

    fun deleteCommentWithPossibleThread(
        behandlingId: UUID,
        documentId: UUID,
        commentId: UUID,
    ) {
        val document = dokumentUnderArbeidService.getDokumentUnderArbeid(documentId)

        dokumentUnderArbeidService.validateWriteAccessToSmartDocument(
            dokumentId = documentId,
            behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(document.behandlingId),
        )

        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()

        val smartEditorId =
            getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )

        val commentOutput = kabalSmartEditorApiGateway.deleteCommentWithPossibleThread(
            documentId = smartEditorId,
            commentId = commentId,
            behandlingTildeltIdent = innloggetSaksbehandlerService.getInnloggetIdent() //simplification for now. Further checks are done in kabal-smarteditor-api.
        )

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                CommentEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    author = Employee(
                        navIdent = commentOutput.author.ident,
                        navn = commentOutput.author.name,
                    ),
                    commentId = commentOutput.id.toString(),
                    text = commentOutput.text,
                    documentId = documentId.toString(),
                    parentId = commentOutput.parentId?.toString(),
                    traceId = currentTraceId(),
                )
            ),
            behandlingId = document.behandlingId,
            type = InternalEventType.SMART_DOCUMENT_COMMENT_REMOVED,
        )
    }

    private fun publishInternalEvent(data: String, behandlingId: UUID, type: InternalEventType) {
        kafkaInternalEventService.publishInternalBehandlingEvent(
            InternalBehandlingEvent(
                behandlingId = behandlingId.toString(),
                type = type,
                data = data,
            )
        )
    }

    private fun getSmartEditorId(dokumentId: UUID, readOnly: Boolean): UUID {
        val dokumentUnderArbeid = dokumentUnderArbeidService.getDokumentUnderArbeid(dokumentId)

        if (dokumentUnderArbeid !is DokumentUnderArbeidAsSmartdokument) {
            throw RuntimeException("dokument is not smartdokument")
        }

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingAndCheckReadAccessToSak(dokumentUnderArbeid.behandlingId)

        return dokumentUnderArbeid.smartEditorId
    }

    private fun getMappedDokumentUnderArbeid(
        dokumentId: UUID,
        smartEditorDocument: SmartDocumentResponse?,
        behandlingId: UUID
    ): DokumentView {
        val behandling = behandlingService.getBehandlingAndCheckReadAccessToSak(
            behandlingId = behandlingId
        )
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeid = dokumentUnderArbeidService.getDokumentUnderArbeid(dokumentId),
            journalpost = null,
            smartEditorDocument = smartEditorDocument,
            behandling = behandling,
        )
    }

}