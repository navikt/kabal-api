package no.nav.klage.dokument.service

import jakarta.transaction.Transactional
import no.nav.klage.dokument.api.view.DocumentValidationResponse
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.dokument.clients.kabaljsontopdf.KabalJsonToPdfClient
import no.nav.klage.dokument.clients.kabalsmarteditorapi.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.domain.FysiskDokument
import no.nav.klage.dokument.domain.PDFDocument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidJournalpostId
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.exceptions.JsonToPdfValidationException
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentMapper
import no.nav.klage.oppgave.clients.kabaldocument.model.request.BrevmottakerInput
import no.nav.klage.oppgave.clients.saf.graphql.Journalpost
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.events.DokumentFerdigstiltAvSaksbehandler
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.addSaksdokument
import no.nav.klage.oppgave.domain.klage.Endringslogginnslag
import no.nav.klage.oppgave.domain.klage.Felt
import no.nav.klage.oppgave.domain.klage.Saksdokument
import no.nav.klage.oppgave.exceptions.InvalidProperty
import no.nav.klage.oppgave.exceptions.SectionedValidationErrorWithDetailsException
import no.nav.klage.oppgave.exceptions.ValidationSection
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.DokumentService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class DokumentUnderArbeidService(
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val attachmentValidator: MellomlagretDokumentValidatorService,
    private val mellomlagerService: MellomlagerService,
    private val smartEditorApiGateway: DefaultKabalSmartEditorApiGateway,
    private val kabalJsonToPdfClient: KabalJsonToPdfClient,
    private val behandlingService: BehandlingService,
    private val dokumentEnhetService: KabalDocumentGateway,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val safClient: SafGraphQlClient,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val dokumentService: DokumentService,
    private val kabalDocumentMapper: KabalDocumentMapper,
    private val eregClient: EregClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        const val SYSTEMBRUKER = "SYSTEMBRUKER"
    }

    fun opprettOgMellomlagreNyttHoveddokument(
        behandlingId: UUID,
        dokumentType: DokumentType,
        opplastetFil: FysiskDokument?,
        innloggetIdent: String,
        tittel: String,
    ): DokumentUnderArbeid {
        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(behandlingId)

        if (opplastetFil == null) {
            throw DokumentValidationException("No file uploaded")
        }

        attachmentValidator.validateAttachment(opplastetFil)
        val mellomlagerId = mellomlagerService.uploadDocument(opplastetFil)
        val hovedDokument = dokumentUnderArbeidRepository.save(
            DokumentUnderArbeid(
                mellomlagerId = mellomlagerId,
                opplastet = LocalDateTime.now(),
                size = opplastetFil.content.size.toLong(),
                name = tittel,
                dokumentType = dokumentType,
                behandlingId = behandlingId,
                smartEditorId = null,
                smartEditorTemplateId = null,
                journalfoertDokumentReference = null,
            )
        )
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_OPPLASTET,
            fraVerdi = null,
            tilVerdi = hovedDokument.created.toString(),
            tidspunkt = hovedDokument.created,
            dokumentId = hovedDokument.id,
        )
        return hovedDokument
    }

    fun opprettSmartdokument(
        behandlingId: UUID,
        dokumentType: DokumentType,
        json: String?,
        smartEditorTemplateId: String?,
        innloggetIdent: String,
        tittel: String,
    ): DokumentUnderArbeid {
        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(behandlingId)

        if (json == null) {
            throw DokumentValidationException("Ingen json angitt")
        }
        val smartEditorDocumentId =
            smartEditorApiGateway.createDocument(
                json = json,
                dokumentType = dokumentType,
                innloggetIdent = innloggetIdent,
                documentTitle = tittel
            )

        val hovedDokument = dokumentUnderArbeidRepository.save(
            DokumentUnderArbeid(
                mellomlagerId = null,
                opplastet = null,
                size = null,
                name = tittel,
                dokumentType = dokumentType,
                behandlingId = behandlingId,
                smartEditorId = smartEditorDocumentId,
                smartEditorTemplateId = smartEditorTemplateId,
                journalfoertDokumentReference = null,
            )
        )
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.SMARTDOKUMENT_OPPRETTET,
            fraVerdi = null,
            tilVerdi = hovedDokument.created.toString(),
            tidspunkt = hovedDokument.created,
            dokumentId = hovedDokument.id,
        )
        return hovedDokument
    }

    fun createJournalfoerteDokumenter(
        parentId: UUID,
        journalfoerteDokumenter: Set<JournalfoertDokumentReference>,
        behandlingId: UUID,
        innloggetIdent: String,
    ): Pair<List<DokumentUnderArbeid>, List<JournalfoertDokumentReference>> {
        val parentDocument = dokumentUnderArbeidRepository.getReferenceById(parentId)

        if (parentDocument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble til et dokument som er ferdigstilt")
        }

        val behandling = behandlingService.getBehandling(behandlingId)

        journalfoerteDokumenter.forEach{
            behandlingService.connectDokumentToBehandling(
                behandlingId = behandlingId,
                journalpostId = it.journalpostId,
                dokumentInfoId = it.dokumentInfoId,
                saksbehandlerIdent = innloggetIdent,
                systemUserContext = false
            )
        }

        val alreadyAddedDocuments =
            dokumentUnderArbeidRepository.findByParentIdAndJournalfoertDokumentReferenceIsNotNull(parentId).map {
                JournalfoertDokumentReference(
                    journalpostId = it.journalfoertDokumentReference!!.journalpostId,
                    dokumentInfoId = it.journalfoertDokumentReference.dokumentInfoId
                )
            }.toSet()

        val (toAdd, duplicates) = journalfoerteDokumenter.partition { it !in alreadyAddedDocuments }

        val resultingDocuments = toAdd.map { journalfoertDokumentReference ->
            val journalpostInDokarkiv =
                safClient.getJournalpostAsSaksbehandler(journalfoertDokumentReference.journalpostId)

            val document = DokumentUnderArbeid(
                mellomlagerId = null,
                opplastet = journalpostInDokarkiv.datoOpprettet,
                size = null,
                name = "Hentes fra SAF",
                dokumentType = null,
                behandlingId = behandlingId,
                smartEditorId = null,
                smartEditorTemplateId = null,
                parentId = parentId,
                journalfoertDokumentReference = no.nav.klage.dokument.domain.dokumenterunderarbeid.JournalfoertDokumentReference(
                    journalpostId = journalfoertDokumentReference.journalpostId,
                    dokumentInfoId = journalfoertDokumentReference.dokumentInfoId,
                )
            )

            behandling.publishEndringsloggEvent(
                saksbehandlerident = innloggetIdent,
                felt = Felt.JOURNALFOERT_DOKUMENT_UNDER_ARBEID_OPPRETTET,
                fraVerdi = null,
                tilVerdi = document.created.toString(),
                tidspunkt = document.created,
                dokumentId = document.id,
            )

            dokumentUnderArbeidRepository.save(
                document
            )
        }

        return resultingDocuments to duplicates
    }

    fun getDokumentUnderArbeid(dokumentId: UUID) = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

    fun updateDokumentType(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        dokumentType: DokumentType,
        innloggetIdent: String
    ): DokumentUnderArbeid {

        val dokumentUnderArbeid = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(dokumentUnderArbeid.behandlingId)

        if (dokumentUnderArbeid.parentId != null) {
            //Vi skal ikke kunne endre dokumentType på vedlegg
            throw DokumentValidationException("Man kan ikke endre dokumentType på vedlegg")
        }

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på et dokument som er ferdigstilt")
        }

        val previousValue = dokumentUnderArbeid.dokumentType
        dokumentUnderArbeid.dokumentType = dokumentType
        dokumentUnderArbeid.modified = LocalDateTime.now()
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_TYPE,
            fraVerdi = previousValue?.id,
            tilVerdi = dokumentUnderArbeid.modified.toString(),
            tidspunkt = dokumentUnderArbeid.modified,
            dokumentId = dokumentUnderArbeid.id,
        )
        return dokumentUnderArbeid
    }

    fun updateDokumentTitle(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        dokumentTitle: String,
        innloggetIdent: String
    ): DokumentUnderArbeid {

        val dokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(dokument.behandlingId)

        if (dokument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre tittel på et dokument som er ferdigstilt")
        }

        val oldValue = dokument.name
        dokument.name = dokumentTitle
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_NAME,
            fraVerdi = oldValue,
            tilVerdi = dokument.name,
            tidspunkt = LocalDateTime.now(),
            dokumentId = dokument.id,
        )
        return dokument
    }

    fun validateDocumentNotFinalized(
        dokumentId: UUID
    ) {
        val dokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)
        if (dokument.erMarkertFerdig()) {
            throw DokumentValidationException("Dokument er allerede ferdigstilt.")
        }
    }

    fun updateSmartEditorTemplateId(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        templateId: String,
        innloggetIdent: String
    ): DokumentUnderArbeid {
        val dokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        if (dokument.smartEditorTemplateId == templateId) {
            return dokument
        }

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingForUpdate(dokument.behandlingId)

        if (dokument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre smartEditorTemplateId på et dokument som er ferdigstilt")
        }

        val oldValue = dokument.smartEditorTemplateId
        dokument.smartEditorTemplateId = templateId
        dokument.modified = LocalDateTime.now()
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.SMARTDOKUMENT_TEMPLATE_ID,
            fraVerdi = oldValue,
            tilVerdi = dokument.smartEditorTemplateId,
            tidspunkt = LocalDateTime.now(),
            dokumentId = dokument.id,
        )
        return dokument
    }

    private fun updateJournalposter(
        behandlingId: UUID,
        dokumentId: UUID,
        journalpostIdSet: Set<DokumentUnderArbeidJournalpostId>
    ): DokumentUnderArbeid {
        val dokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        val behandling = behandlingService.getBehandlingForUpdateBySystembruker(behandlingId)

        val oldValue = dokument.journalposter
        dokument.journalposter.clear()
        dokument.journalposter.addAll(journalpostIdSet)

        behandling.publishEndringsloggEvent(
            saksbehandlerident = SYSTEMBRUKER,
            felt = Felt.DOKUMENT_UNDER_ARBEID_JOURNALPOST_ID,
            fraVerdi = oldValue.joinToString { it.journalpostId },
            tilVerdi = dokument.journalposter.joinToString { it.journalpostId },
            tidspunkt = LocalDateTime.now(),
            dokumentId = dokument.id,
        )

        return dokument
    }

    fun validateSmartDokument(
        dokumentId: UUID
    ): List<DocumentValidationResponse> {
        val documentValidationResults = mutableListOf<DocumentValidationResponse>()

        val hovedDokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)
        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)
        if (hovedDokument.smartEditorId != null) {
            documentValidationResults += validateSingleDocument(hovedDokument)
        }
        vedlegg.forEach {
            if (it.smartEditorId != null) {
                documentValidationResults += validateSingleDocument(it)
            }
        }

        return documentValidationResults
    }

    private fun validateSingleDocument(dokument: DokumentUnderArbeid): DocumentValidationResponse {
        logger.debug("Getting json document, dokumentId: {}", dokument.id)
        val documentJson = smartEditorApiGateway.getDocumentAsJson(dokument.smartEditorId!!)
        logger.debug("Validating json document in kabalJsontoPdf, dokumentId: {}", dokument.id)
        val response = kabalJsonToPdfClient.validateJsonDocument(documentJson)
        return DocumentValidationResponse(
            dokumentId = dokument.id.toString(),
            errors = response.errors.map {
                DocumentValidationResponse.DocumentValidationError(
                    type = it.type,
                    paths = it.paths,
                )
            }
        )
    }

    fun finnOgMarkerFerdigHovedDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        ident: String,
        brevmottakerIdents: Set<String>?,
    ): DokumentUnderArbeid {
        val hovedDokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)
        val behandling = behandlingService.getBehandlingForUpdate(hovedDokument.behandlingId)

        validateBeforeFerdig(
            brevmottakerIdents = brevmottakerIdents,
            hovedDokument = hovedDokument,
            behandling = behandling,
        )

        val now = LocalDateTime.now()
        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)
        hovedDokument.markerFerdigHvisIkkeAlleredeMarkertFerdig(tidspunkt = now, saksbehandlerIdent = ident)
        hovedDokument.brevmottakerIdents = brevmottakerInputs(
            brevmottakerIdents = brevmottakerIdents,
            behandling = behandling,
            hovedDokument = hovedDokument,
        ).map {
            it.partId.value
        }.toSet()
        vedlegg.forEach { it.markerFerdigHvisIkkeAlleredeMarkertFerdig(tidspunkt = now, saksbehandlerIdent = ident) }

        //Etter at et dokument er markert som ferdig skal det ikke kunne endres. Vi henter derfor en snapshot av tilstanden slik den er nå
        if (hovedDokument.smartEditorId != null) {
            mellomlagreNyVersjonAvSmartEditorDokument(hovedDokument)
        }
        vedlegg.forEach {
            if (it.smartEditorId != null) {
                mellomlagreNyVersjonAvSmartEditorDokument(it)
            }
        }

        behandling.publishEndringsloggEvent(
            saksbehandlerident = ident,
            felt = Felt.DOKUMENT_UNDER_ARBEID_MARKERT_FERDIG,
            fraVerdi = null,
            tilVerdi = hovedDokument.markertFerdig.toString(),
            tidspunkt = LocalDateTime.now(),
            dokumentId = hovedDokument.id,
        )

        behandling.publishEndringsloggEvent(
            saksbehandlerident = ident,
            felt = Felt.DOKUMENT_UNDER_ARBEID_BREVMOTTAKER_IDENTS,
            fraVerdi = null,
            tilVerdi = hovedDokument.brevmottakerIdents.joinToString { it },
            tidspunkt = LocalDateTime.now(),
            dokumentId = hovedDokument.id,
        )

        applicationEventPublisher.publishEvent(DokumentFerdigstiltAvSaksbehandler(hovedDokument))

        return hovedDokument
    }

    private fun validateBeforeFerdig(
        brevmottakerIdents: Set<String>?,
        hovedDokument: DokumentUnderArbeid,
        behandling: Behandling,
    ) {
        val documentValidationErrors = validateSmartDokument(hovedDokument.id)
        if (documentValidationErrors.any { it.errors.isNotEmpty() }) {
            throw JsonToPdfValidationException(
                msg = "Validation error from json to pdf",
                errors = documentValidationErrors
            )
        }

        if (hovedDokument.dokumentType != DokumentType.NOTAT && brevmottakerIdents.isNullOrEmpty()) {
            throw DokumentValidationException("Brevmottakere må være satt")
        }

        if (hovedDokument.erMarkertFerdig() || hovedDokument.erFerdigstilt()) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på et dokument som er ferdigstilt")
        }

        if (hovedDokument.parentId != null) {
            throw DokumentValidationException("Kan ikke markere et vedlegg som ferdig")
        }

        val mottakere = brevmottakerInputs(brevmottakerIdents, behandling, hovedDokument)

        val invalidProperties = mutableListOf<InvalidProperty>()

        //Could ignore NOTAT here. We'll see.
        mottakere.forEach { mottaker ->
            if (mottaker.partId.partIdTypeId == PartIdType.VIRKSOMHET.id) {
                val organisasjon = eregClient.hentOrganisasjon(mottaker.partId.value)
                if (!organisasjon.isActive()) {
                    invalidProperties += InvalidProperty(
                        field = mottaker.partId.value,
                        reason = "Organisasjon er avviklet.",
                    )
                }
            }
        }

        if (invalidProperties.isNotEmpty()) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Ferdigstilling av dokument",
                sections = listOf(
                    ValidationSection(
                        section = "mottakere",
                        properties = invalidProperties,
                    )
                )
            )
        }
    }

    private fun brevmottakerInputs(
        brevmottakerIdents: Set<String>?,
        behandling: Behandling,
        hovedDokument: DokumentUnderArbeid,
    ): List<BrevmottakerInput> {

        return kabalDocumentMapper.mapBrevmottakerIdentToBrevmottakerInput(
            behandling = behandling,
            brevmottakerIdents = brevmottakerIdents,
            dokumentType = hovedDokument.dokumentType!!
        )
    }

    fun getFysiskDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        innloggetIdent: String
    ): FysiskDokument {
        val dokument =
            dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandling(dokument.behandlingId)

        val (content, title) = when (dokument.getType()) {
            DokumentUnderArbeid.DokumentUnderArbeidType.UPLOADED -> {
                mellomlagerService.getUploadedDocument(dokument.mellomlagerId!!) to dokument.name
            }

            DokumentUnderArbeid.DokumentUnderArbeidType.SMART -> {
                if (dokument.isStaleSmartEditorDokument()) {
                    mellomlagreNyVersjonAvSmartEditorDokument(dokument).bytes to dokument.name
                } else mellomlagerService.getUploadedDocument(dokument.mellomlagerId!!) to dokument.name
            }

            DokumentUnderArbeid.DokumentUnderArbeidType.JOURNALFOERT -> {
                val fysiskDokument = dokumentService.getFysiskDokument(
                    journalpostId = dokument.journalfoertDokumentReference!!.journalpostId,
                    dokumentInfoId = dokument.journalfoertDokumentReference.dokumentInfoId,
                )
                fysiskDokument.content to fysiskDokument.title
            }
        }

        return FysiskDokument(
            title = title,
            content = dokumentService.changeTitleInPDF(content, title),
            contentType = MediaType.APPLICATION_PDF
        )
    }

    fun slettDokument(
        dokumentId: UUID,
        innloggetIdent: String,
    ) {
        val document = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandling(
            behandlingId = document.behandlingId,
        )

        val descendants = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(document.id)

        val documents = descendants.plus(document)

        documents.forEach { currentDocument ->
            if (currentDocument.erMarkertFerdig()) {
                logger.warn("Attempting to delete finalized document {}", currentDocument.id)
            }

            try {
                if (currentDocument.smartEditorId != null) {
                    smartEditorApiGateway.deleteDocument(currentDocument.smartEditorId!!)
                }
            } catch (e: Exception) {
                logger.warn("Couldn't delete smartEditor document", e)
            }

            try {
                if (currentDocument.mellomlagerId != null) {
                    mellomlagerService.deleteDocument(currentDocument.mellomlagerId!!)
                }
            } catch (e: Exception) {
                logger.warn("Couldn't delete mellomlager document", e)
            }

            dokumentUnderArbeidRepository.delete(currentDocument)

            behandling.publishEndringsloggEvent(
                saksbehandlerident = innloggetIdent,
                felt = Felt.DOKUMENT_UNDER_ARBEID_OPPLASTET,
                fraVerdi = currentDocument.opplastet.toString(),
                tilVerdi = null,
                tidspunkt = LocalDateTime.now(),
                dokumentId = currentDocument.id,
            )
        }
    }

    fun setParentDocument(
        parentId: UUID,
        dokumentId: UUID,
        innloggetIdent: String
    ): Pair<List<DokumentUnderArbeid>, List<DokumentUnderArbeid>> {
        if (parentId == dokumentId) {
            throw DokumentValidationException("Kan ikke gjøre et dokument til vedlegg for seg selv.")
        }
        val parentDokument = dokumentUnderArbeidRepository.getReferenceById(parentId)
        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingForUpdate(parentDokument.behandlingId)

        if (parentDokument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble til et dokument som er ferdigstilt")
        }

        val descendants = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(dokumentId)

        val dokumentIdSet = mutableSetOf<UUID>()
        dokumentIdSet += dokumentId
        dokumentIdSet.addAll(descendants.map { it.id })

        val processedDokumentUnderArbeidOutput = dokumentIdSet.map { currentDokumentId ->
            setParentInDokumentUnderArbeidAndFindDuplicates(currentDokumentId, parentId)
        }

        val alteredDocuments = processedDokumentUnderArbeidOutput.mapNotNull { it.first }
        val duplicateJournalfoerteDokumenterUnderArbeid = processedDokumentUnderArbeidOutput.mapNotNull { it.second }

        duplicateJournalfoerteDokumenterUnderArbeid.forEach {
            dokumentUnderArbeidRepository.deleteById(it.id)
        }

        return alteredDocuments to duplicateJournalfoerteDokumenterUnderArbeid
    }

    private fun setParentInDokumentUnderArbeidAndFindDuplicates(
        currentDokumentId: UUID,
        parentId: UUID,
    ): Pair<DokumentUnderArbeid?, DokumentUnderArbeid?> {
        val dokumentUnderArbeid =
            dokumentUnderArbeidRepository.getReferenceById(currentDokumentId)

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble et dokument som er ferdigstilt")
        }

        return if (dokumentUnderArbeid.getType() == DokumentUnderArbeid.DokumentUnderArbeidType.JOURNALFOERT) {
            if (dokumentUnderArbeidRepository.findByParentIdAndJournalfoertDokumentReferenceAndIdNot(
                    parentId = parentId,
                    journalfoertDokumentReference = dokumentUnderArbeid.journalfoertDokumentReference!!,
                    id = currentDokumentId,
                ).isNotEmpty()
            ) {
                logger.warn("Dette journalførte dokumentet er allerede lagt til som vedlegg på dette dokumentet.")
                null to dokumentUnderArbeid
            } else {
                dokumentUnderArbeid.parentId = parentId
                dokumentUnderArbeid to null
            }
        } else {
            dokumentUnderArbeid.parentId = parentId
            dokumentUnderArbeid to null
        }
    }

    fun frikobleVedlegg(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        innloggetIdent: String
    ): DokumentUnderArbeid {
        val vedlegg = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingForUpdate(vedlegg.behandlingId)
        //TODO: Skal det lages endringslogg på dette??

        if (vedlegg.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke frikoble et dokument som er ferdigstilt")
        }

        vedlegg.parentId = null
        return vedlegg
    }

    fun findDokumenterNotFinished(behandlingId: UUID, checkReadAccess: Boolean = true): List<DokumentUnderArbeid> {
        //Sjekker tilgang på behandlingsnivå:
        if (checkReadAccess) {
            behandlingService.getBehandling(behandlingId)
        }

        return dokumentUnderArbeidRepository.findByBehandlingIdAndFerdigstiltIsNullOrderByCreatedDesc(behandlingId)
    }

    fun getSmartDokumenterUnderArbeid(behandlingId: UUID, ident: String): SortedSet<DokumentUnderArbeid> {
        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandling(behandlingId)

        return dokumentUnderArbeidRepository.findByBehandlingIdAndSmartEditorIdNotNullAndMarkertFerdigIsNullOrderByCreated(
            behandlingId
        )
    }

    fun opprettDokumentEnhet(hovedDokumentId: UUID): DokumentUnderArbeid {
        val hovedDokument = dokumentUnderArbeidRepository.getReferenceById(hovedDokumentId)
        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)
        //Denne er alltid sann
        if (hovedDokument.dokumentEnhetId == null) {
            //Vi vet at smartEditor-dokumentene har en oppdatert snapshot i mellomlageret fordi det ble fikset i finnOgMarkerFerdigHovedDokument
            val behandling = behandlingService.getBehandlingForUpdateBySystembruker(hovedDokument.behandlingId)
            val dokumentEnhetId = dokumentEnhetService.createKomplettDokumentEnhet(behandling, hovedDokument, vedlegg)
            hovedDokument.dokumentEnhetId = dokumentEnhetId
        }
        return hovedDokument
    }

    fun ferdigstillDokumentEnhet(hovedDokumentId: UUID): DokumentUnderArbeid {
        val hovedDokument = dokumentUnderArbeidRepository.getReferenceById(hovedDokumentId)
        val vedlegg = dokumentUnderArbeidRepository.findByParentIdOrderByCreated(hovedDokument.id)
        val behandling: Behandling = behandlingService.getBehandlingForUpdateBySystembruker(hovedDokument.behandlingId)
        val documentInfoList =
            dokumentEnhetService.fullfoerDokumentEnhet(dokumentEnhetId = hovedDokument.dokumentEnhetId!!)

        documentInfoList.forEach { documentInfo ->
            val journalpost = safClient.getJournalpostAsSystembruker(documentInfo.journalpostId.value)

            val saksdokumenter = journalpost.mapToSaksdokumenter()
            saksdokumenter.forEach { saksdokument ->
                val saksbehandlerIdent = SYSTEMBRUKER
                behandling.addSaksdokument(saksdokument, saksbehandlerIdent)
                    ?.also { applicationEventPublisher.publishEvent(it) }
            }
        }

        updateJournalposter(
            behandlingId = behandling.id,
            dokumentId = hovedDokumentId,
            journalpostIdSet = HashSet(documentInfoList.map { DokumentUnderArbeidJournalpostId(journalpostId = it.journalpostId.value) })
        )

        val now = LocalDateTime.now()

        hovedDokument.ferdigstillHvisIkkeAlleredeFerdigstilt(now)
        if (hovedDokument.smartEditorId != null) {
            try {
                smartEditorApiGateway.deleteDocumentAsSystemUser(hovedDokument.smartEditorId!!)
            } catch (e: Exception) {
                logger.warn("Couldn't delete hoveddokument from smartEditorApi", e)
            }
        }

        vedlegg.forEach {
            it.ferdigstillHvisIkkeAlleredeFerdigstilt(now)
            try {
                smartEditorApiGateway.deleteDocumentAsSystemUser(it.smartEditorId!!)
            } catch (e: Exception) {
                logger.warn("Couldn't delete vedlegg from smartEditorApi", e)
            }
        }

        return hovedDokument
    }

    fun getSmartEditorId(dokumentId: UUID, readOnly: Boolean): UUID {
        val dokumentUnderArbeid = dokumentUnderArbeidRepository.getReferenceById(dokumentId)
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()

        //Sjekker tilgang på behandlingsnivå:
        if (readOnly) {
            behandlingService.getBehandling(dokumentUnderArbeid.behandlingId)
        } else {
            behandlingService.getBehandlingForWriteAllowROLAndMU(
                behandlingId = dokumentUnderArbeid.behandlingId,
                utfoerendeSaksbehandlerIdent = ident,
            )
        }

        return dokumentUnderArbeid.smartEditorId
            ?: throw DokumentValidationException("$dokumentId er ikke et smarteditor dokument")
    }

    private fun mellomlagreNyVersjonAvSmartEditorDokument(dokument: DokumentUnderArbeid): PDFDocument {
        val documentJson = smartEditorApiGateway.getDocumentAsJson(dokument.smartEditorId!!)
        val pdfDocument = kabalJsonToPdfClient.getPDFDocument(documentJson)
        val mellomlagerId =
            mellomlagerService.uploadByteArray(
                tittel = dokument.name,
                content = pdfDocument.bytes
            )

        if (dokument.mellomlagerId != null) {
            mellomlagerService.deleteDocument(dokument.mellomlagerId!!)
        }

        dokument.mellomlagerId = mellomlagerId
        dokument.size = pdfDocument.bytes.size.toLong()
        dokument.opplastet = LocalDateTime.now()

        return pdfDocument
    }

    private fun DokumentUnderArbeid.isStaleSmartEditorDokument() =
        this.smartEditorId != null && !this.erMarkertFerdig() && smartEditorApiGateway.isMellomlagretDokumentStale(
            smartEditorId = this.smartEditorId!!,
            sistOpplastet = this.opplastet
        )

    private fun Behandling.endringslogg(
        saksbehandlerident: String,
        felt: Felt,
        fraVerdi: String?,
        tilVerdi: String?,
        tidspunkt: LocalDateTime
    ): Endringslogginnslag? {
        return Endringslogginnslag.endringslogg(
            saksbehandlerident,
            felt,
            fraVerdi,
            tilVerdi,
            this.id,
            tidspunkt
        )
    }

    private fun Behandling.publishEndringsloggEvent(
        saksbehandlerident: String,
        felt: Felt,
        fraVerdi: String?,
        tilVerdi: String?,
        tidspunkt: LocalDateTime,
        dokumentId: UUID,
    ) {
        listOfNotNull(
            this.endringslogg(
                saksbehandlerident = saksbehandlerident,
                felt = Felt.DOKUMENT_UNDER_ARBEID_ID,
                fraVerdi = fraVerdi.let { dokumentId.toString() },
                tilVerdi = tilVerdi.let { dokumentId.toString() },
                tidspunkt = tidspunkt,
            ),
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

    private fun Journalpost?.mapToSaksdokumenter(): List<Saksdokument> {
        return this?.dokumenter?.map {
            Saksdokument(
                journalpostId = this.journalpostId,
                dokumentInfoId = it.dokumentInfoId
            )
        } ?: emptyList()
    }
}


