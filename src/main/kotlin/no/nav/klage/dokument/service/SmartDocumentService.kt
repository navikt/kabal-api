package no.nav.klage.dokument.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.transaction.Transactional
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.*
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.CommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.request.ModifyCommentInput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.CommentOutput
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.SmartDocumentResponse
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsSmartdokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Language
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsVedlegg
import no.nav.klage.dokument.gateway.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.dokument.repositories.SmartdokumentUnderArbeidAsHoveddokumentRepository
import no.nav.klage.dokument.repositories.SmartdokumentUnderArbeidAsVedleggRepository
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag
import no.nav.klage.oppgave.domain.klage.Felt
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.KafkaInternalEventService
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class SmartDocumentService(
    private val kabalSmartEditorApiGateway: DefaultKabalSmartEditorApiGateway,
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val smartDokumentUnderArbeidAsHoveddokumentRepository: SmartdokumentUnderArbeidAsHoveddokumentRepository,
    private val smartDokumentUnderArbeidAsVedleggRepository: SmartdokumentUnderArbeidAsVedleggRepository,
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val dokumentMapper: DokumentMapper,
    private val saksbehandlerService: SaksbehandlerService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        private val objectMapper: ObjectMapper = ourJacksonObjectMapper()
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

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        val behandlingRole = dokumentUnderArbeidService.validateCanCreateDocumentsAndReturnBehandlingRole(
            behandling = behandling,
            innloggetIdent = innloggetIdent,
            parentId = parentId,
        )

        val smartDocumentResponse =
            kabalSmartEditorApiGateway.createDocument(
                json = input.content.toString(),
                data = input.data,
                dokumentType = dokumentType,
                innloggetIdent = innloggetIdent,
                documentTitle = tittel,
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
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.SMARTDOKUMENT_OPPRETTET,
            fraVerdi = null,
            tilVerdi = document.created.toString(),
            tidspunkt = document.created,
        )

        val smartEditorDocument =
            kabalSmartEditorApiGateway.getSmartDocumentResponse(smartEditorId = document.smartEditorId)

        val dokumentView = dokumentMapper.mapToDokumentView(
            dokumentUnderArbeid = document,
            journalpost = null,
            smartEditorDocument = smartEditorDocument,
            behandling = behandling,
        )

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                DocumentsAddedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    documents = listOf(
                        dokumentView
                    )
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.DOCUMENTS_ADDED,
        )

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

        dokumentUnderArbeidService.validateWriteAccessToDocument(dokumentId = dokumentId)

        val updatedDocument = kabalSmartEditorApiGateway.updateDocument(
            smartDocumentId = smartDocumentId,
            json = input.content.toString(),
            data = input.data,
            currentVersion = input.version,
        )

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
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

    fun getDocumentAccess(
        behandlingId: UUID,
        documentId: UUID,
    ): DocumentAccessView {
        try {
            dokumentUnderArbeidService.validateWriteAccessToDocument(dokumentId = documentId)
            return DocumentAccessView(
                access = DocumentAccessView.Access.WRITE,
            )
        } catch (e: Exception) {
            return DocumentAccessView(
                access = DocumentAccessView.Access.NONE,
            )
        }
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

        return ourJacksonObjectMapper().readTree(document.json)
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
        dokumentUnderArbeidService.validateWriteAccessToDocument(documentId)
        val document = dokumentUnderArbeidRepository.findById(documentId).get()

        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()

        val smartEditorId =
            getSmartEditorId(
                dokumentId = documentId,
                readOnly = true
            )

        val commentOutput = kabalSmartEditorApiGateway.createComment(smartEditorId, commentInput)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
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
        dokumentUnderArbeidService.validateWriteAccessToDocument(documentId)
        val document = dokumentUnderArbeidRepository.findById(documentId).get()
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
            data = objectMapper.writeValueAsString(
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
        dokumentUnderArbeidService.validateWriteAccessToDocument(documentId)
        val document = dokumentUnderArbeidRepository.findById(documentId).get()
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
            data = objectMapper.writeValueAsString(
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
        dokumentUnderArbeidService.validateWriteAccessToDocument(documentId)
        val document = dokumentUnderArbeidRepository.findById(documentId).get()
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
            data = objectMapper.writeValueAsString(
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

    private fun Behandling.endringslogg(
        saksbehandlerident: String,
        felt: Felt,
        fraVerdi: String?,
        tilVerdi: String?,
        tidspunkt: LocalDateTime
    ): Endringslogginnslag? {
        return Endringslogginnslag.endringslogg(
            saksbehandlerident = saksbehandlerident,
            felt = felt,
            fraVerdi = fraVerdi,
            tilVerdi = tilVerdi,
            behandlingId = this.id,
            tidspunkt = tidspunkt
        )
    }

    private fun Behandling.publishEndringsloggEvent(
        saksbehandlerident: String,
        felt: Felt,
        fraVerdi: String?,
        tilVerdi: String?,
        tidspunkt: LocalDateTime,
    ) {
        listOfNotNull(
            this.endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = felt,
                fraVerdi = fraVerdi,
                tilVerdi = tilVerdi,
                tidspunkt = tidspunkt,
            )
        ).let {
            applicationEventPublisher.publishEvent(
                BehandlingEndretEvent(
                    behandling = this,
                    endringslogginnslag = it
                )
            )
        }
    }

    private fun getSmartEditorId(dokumentId: UUID, readOnly: Boolean): UUID {
        val dokumentUnderArbeid = dokumentUnderArbeidRepository.findById(dokumentId).get()

        if (dokumentUnderArbeid !is DokumentUnderArbeidAsSmartdokument) {
            throw RuntimeException("dokument is not smartdokument")
        }

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(dokumentUnderArbeid.behandlingId)

        return dokumentUnderArbeid.smartEditorId
    }

    private fun getMappedDokumentUnderArbeid(
        dokumentId: UUID,
        smartEditorDocument: SmartDocumentResponse?,
        behandlingId: UUID
    ): DokumentView {
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(
            behandlingId = behandlingId
        )
        return dokumentMapper.mapToDokumentView(
            dokumentUnderArbeid = getDokumentUnderArbeid(dokumentId),
            journalpost = null,
            smartEditorDocument = smartEditorDocument,
            behandling = behandling,
        )
    }

    private fun getDokumentUnderArbeid(dokumentId: UUID) = dokumentUnderArbeidRepository.findById(dokumentId).get()
}