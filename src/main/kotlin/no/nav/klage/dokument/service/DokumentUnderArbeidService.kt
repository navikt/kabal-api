package no.nav.klage.dokument.service

import jakarta.transaction.Transactional
import no.nav.klage.dokument.api.view.DocumentValidationResponse
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.dokument.clients.kabaljsontopdf.KabalJsonToPdfClient
import no.nav.klage.dokument.clients.kabalsmarteditorapi.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.domain.FysiskDokument
import no.nav.klage.dokument.domain.PDFDocument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.*
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.exceptions.JsonToPdfValidationException
import no.nav.klage.dokument.repositories.*
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Template
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentMapper
import no.nav.klage.oppgave.clients.saf.graphql.Journalpost
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.events.DokumentFerdigstiltAvSaksbehandler
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.addSaksdokument
import no.nav.klage.oppgave.exceptions.InvalidProperty
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.exceptions.SectionedValidationErrorWithDetailsException
import no.nav.klage.oppgave.exceptions.ValidationSection
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.DokumentService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class DokumentUnderArbeidService(
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val dokumentUnderArbeidCommonService: DokumentUnderArbeidCommonService,
    private val opplastetDokumentUnderArbeidAsHoveddokumentRepository: OpplastetDokumentUnderArbeidAsHoveddokumentRepository,
    private val opplastetDokumentUnderArbeidAsVedleggRepository: OpplastetDokumentUnderArbeidAsVedleggRepository,
    private val smartDokumentUnderArbeidAsHoveddokumentRepository: SmartdokumentUnderArbeidAsHoveddokumentRepository,
    private val smartDokumentUnderArbeidAsVedleggRepository: SmartdokumentUnderArbeidAsVedleggRepository,
    private val journalfoertDokumentUnderArbeidRepository: JournalfoertDokumentUnderArbeidAsVedleggRepository,
    private val attachmentValidator: MellomlagretDokumentValidatorService,
    private val mellomlagerService: MellomlagerService,
    private val smartEditorApiGateway: DefaultKabalSmartEditorApiGateway,
    private val kabalJsonToPdfClient: KabalJsonToPdfClient,
    private val behandlingService: BehandlingService,
    private val kabalDocumentGateway: KabalDocumentGateway,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val safClient: SafGraphQlClient,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val dokumentService: DokumentService,
    private val kabalDocumentMapper: KabalDocumentMapper,
    private val eregClient: EregClient,
    private val innholdsfortegnelseService: InnholdsfortegnelseService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securLogger = getSecureLogger()
        const val SYSTEMBRUKER = "SYSTEMBRUKER"
    }

    fun createOpplastetDokumentUnderArbeid(
        behandlingId: UUID,
        dokumentType: DokumentType,
        opplastetFil: FysiskDokument?,
        innloggetIdent: String,
        tittel: String,
        parentId: UUID?,
    ): DokumentUnderArbeid {
        //Sjekker lesetilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        validateCanCreateDocuments(
            behandlingRole = behandlingRole,
            parentDocument = if (parentId != null) dokumentUnderArbeidRepository.getReferenceById(parentId) else null
        )

        if (opplastetFil == null) {
            throw DokumentValidationException("No file uploaded")
        }

        attachmentValidator.validateAttachment(opplastetFil)
        val mellomlagerId = mellomlagerService.uploadDocument(opplastetFil)

        val now = LocalDateTime.now()

        val document = if (parentId == null) {
            opplastetDokumentUnderArbeidAsHoveddokumentRepository.save(
                OpplastetDokumentUnderArbeidAsHoveddokument(
                    mellomlagerId = mellomlagerId,
                    mellomlagretDate = now,
                    size = opplastetFil.content.size.toLong(),
                    name = tittel,
                    dokumentType = dokumentType,
                    behandlingId = behandlingId,
                    creatorIdent = innloggetIdent,
                    creatorRole = behandlingRole,
                    created = now,
                    modified = now,
                )
            )
        } else {
            opplastetDokumentUnderArbeidAsVedleggRepository.save(
                OpplastetDokumentUnderArbeidAsVedlegg(
                    mellomlagerId = mellomlagerId,
                    mellomlagretDate = now,
                    size = opplastetFil.content.size.toLong(),
                    name = tittel,
                    behandlingId = behandlingId,
                    creatorIdent = innloggetIdent,
                    creatorRole = behandlingRole,
                    parentId = parentId,
                    created = now,
                    modified = now,
                    dokumentType = dokumentType,
                )
            )
        }
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_OPPLASTET,
            fraVerdi = null,
            tilVerdi = document.created.toString(),
            tidspunkt = document.created,
            dokumentId = document.id,
        )

        return document
    }

    fun opprettSmartdokument(
        behandlingId: UUID,
        dokumentType: DokumentType,
        json: String?,
        smartEditorTemplateId: String,
        innloggetIdent: String,
        tittel: String,
        parentId: UUID?,
    ): DokumentUnderArbeid {
        //Sjekker lesetilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        validateCanCreateDocuments(
            behandlingRole = behandlingRole,
            parentDocument = if (parentId != null) dokumentUnderArbeidRepository.getReferenceById(parentId) else null
        )

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
                    smartEditorId = smartEditorDocumentId,
                    smartEditorTemplateId = smartEditorTemplateId,
                    creatorIdent = innloggetIdent,
                    creatorRole = behandlingRole,
                    created = now,
                    modified = now,
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
                    smartEditorId = smartEditorDocumentId,
                    smartEditorTemplateId = smartEditorTemplateId,
                    creatorIdent = innloggetIdent,
                    creatorRole = behandlingRole,
                    parentId = parentId,
                    created = now,
                    modified = now,
                    dokumentType = dokumentType,
                )
            )
        }
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.SMARTDOKUMENT_OPPRETTET,
            fraVerdi = null,
            tilVerdi = document.created.toString(),
            tidspunkt = document.created,
            dokumentId = document.id,
        )

        return document
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

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        if (behandling.avsluttetAvSaksbehandler != null) {
            val isCurrentROL = behandling.rolIdent == innloggetIdent

            journalfoerteDokumenter.forEach {
                behandlingService.connectDokumentToBehandling(
                    behandlingId = behandlingId,
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId,
                    saksbehandlerIdent = innloggetIdent,
                    systemUserContext = false,
                    ignoreCheckSkrivetilgang = isCurrentROL,
                )
            }
        }

        val alreadyAddedDocuments =
            journalfoertDokumentUnderArbeidRepository.findByParentId(parentId)
                .map {
                    JournalfoertDokumentReference(
                        journalpostId = it.journalpostId,
                        dokumentInfoId = it.dokumentInfoId
                    )
                }.toSet()

        val (toAdd, duplicates) = journalfoerteDokumenter.partition { it !in alreadyAddedDocuments }

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        validateCanCreateDocuments(
            behandlingRole = behandlingRole,
            parentDocument = parentDocument
        )

        val now = LocalDateTime.now()

        val resultingDocuments = toAdd.map { journalfoertDokumentReference ->
            val journalpostInDokarkiv =
                safClient.getJournalpostAsSaksbehandler(journalfoertDokumentReference.journalpostId)

            val document = JournalfoertDokumentUnderArbeidAsVedlegg(
                name = getDokumentTitle(
                    journalpost = journalpostInDokarkiv,
                    dokumentInfoId = journalfoertDokumentReference.dokumentInfoId
                ),
                behandlingId = behandlingId,
                parentId = parentId,
                journalpostId = journalfoertDokumentReference.journalpostId,
                dokumentInfoId = journalfoertDokumentReference.dokumentInfoId,
                creatorIdent = innloggetIdent,
                creatorRole = behandlingRole,
                opprettet = journalpostInDokarkiv.datoOpprettet,
                created = now,
                modified = now,
                markertFerdig = null,
                markertFerdigBy = null,
                ferdigstilt = null,
                dokumentType = null,
            )

            behandling.publishEndringsloggEvent(
                saksbehandlerident = innloggetIdent,
                felt = Felt.JOURNALFOERT_DOKUMENT_UNDER_ARBEID_OPPRETTET,
                fraVerdi = null,
                tilVerdi = document.created.toString(),
                tidspunkt = document.created,
                dokumentId = document.id,
            )

            journalfoertDokumentUnderArbeidRepository.save(
                document
            )
        }

        return resultingDocuments to duplicates
    }

    private fun getDokumentTitle(journalpost: Journalpost, dokumentInfoId: String): String {
        return journalpost.dokumenter?.find { it.dokumentInfoId == dokumentInfoId }?.tittel
            ?: error("can't be null")
    }

    private fun Behandling.getRoleInBehandling(innloggetIdent: String) = if (rolIdent == innloggetIdent) {
        BehandlingRole.KABAL_ROL
    } else if (tildeling?.saksbehandlerident == innloggetIdent) {
        BehandlingRole.KABAL_SAKSBEHANDLING
    } else if (medunderskriver?.saksbehandlerident == innloggetIdent) {
        BehandlingRole.KABAL_MEDUNDERSKRIVER
    } else BehandlingRole.NONE

    private fun validateCanCreateDocuments(behandlingRole: BehandlingRole, parentDocument: DokumentUnderArbeid?) {
        if (behandlingRole !in listOf(BehandlingRole.KABAL_ROL, BehandlingRole.KABAL_SAKSBEHANDLING)) {
            throw MissingTilgangException("Kun ROL eller saksbehandler kan opprette dokumenter")
        }

        if (behandlingRole == BehandlingRole.KABAL_ROL && parentDocument == null) {
            throw MissingTilgangException("ROL kan ikke opprette hoveddokumenter.")
        }

        if (parentDocument != null && behandlingRole == BehandlingRole.KABAL_ROL) {
            if (!(parentDocument is SmartdokumentUnderArbeidAsHoveddokument && parentDocument.smartEditorTemplateId == Template.ROL_QUESTIONS.id)) {
                throw MissingTilgangException("ROL kan ikke opprette vedlegg til dette hoveddokumentet.")
            }
        }
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
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        if (dokumentUnderArbeid.isVedlegg()) {
            //Vi skal ikke kunne endre dokumentType på vedlegg
            throw DokumentValidationException("Man kan ikke endre dokumentType på vedlegg")
        }

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på et dokument som er ferdigstilt")
        }

        if (dokumentUnderArbeid !is DokumentUnderArbeidAsHoveddokument) {
            throw RuntimeException("dokumentType cannot be set for this type of document.")
        }

        val previousValue = dokumentUnderArbeid.dokumentType
        dokumentUnderArbeid.dokumentType = dokumentType

        dokumentUnderArbeid.modified = LocalDateTime.now()

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_TYPE,
            fraVerdi = previousValue?.id,
            tilVerdi = dokumentUnderArbeid.dokumentType.toString(),
            tidspunkt = dokumentUnderArbeid.modified,
            dokumentId = dokumentUnderArbeid.id,
        )

        return dokumentUnderArbeid
    }

    private fun DokumentUnderArbeid.isVedlegg(): Boolean {
        return this is SmartdokumentUnderArbeidAsVedlegg ||
                this is OpplastetDokumentUnderArbeidAsVedlegg ||
                this is JournalfoertDokumentUnderArbeidAsVedlegg
    }

    fun updateDokumentTitle(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        dokumentTitle: String,
        innloggetIdent: String
    ): DokumentUnderArbeid {

        val dokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(dokument.behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        if (dokument.creatorRole != behandlingRole) {
            throw MissingTilgangException("$behandlingRole har ikke anledning til å endre tittel på dette dokumentet eiet av ${dokument.creatorRole}.")
        }

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

    fun validateDocument(
        dokumentId: UUID,
    ) {
        val dokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)
        if (dokument.erMarkertFerdig()) {
            throw DokumentValidationException("Dokument er allerede ferdigstilt.")
        }

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(dokument.behandlingId)

        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        when (dokument.creatorRole) {
            BehandlingRole.KABAL_SAKSBEHANDLING -> {
                if (behandlingRole !in listOf(
                        BehandlingRole.KABAL_SAKSBEHANDLING,
                        BehandlingRole.KABAL_MEDUNDERSKRIVER
                    )
                ) {
                    throw MissingTilgangException("Kun saksbehandler eller medunderskriver kan skrive i dette dokumentet.")
                }
            }

            BehandlingRole.KABAL_ROL -> {
                if (behandlingRole != BehandlingRole.KABAL_ROL) {
                    throw MissingTilgangException("Kun ROL kan skrive i dette dokumentet.")
                }
            }

            else -> {
                throw RuntimeException("A document was created by non valid role: ${dokument.creatorRole}")
            }
        }
    }

    fun updateSmartEditorTemplateId(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        templateId: String,
        innloggetIdent: String
    ): DokumentUnderArbeid {
        val dokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        if (dokument !is DokumentUnderArbeidAsSmartdokument) {
            throw RuntimeException("Not a smartdocument")
        }

        if (dokument.smartEditorTemplateId == templateId) {
            return dokument
        }

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

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

    fun validateIfSmartDokument(
        dokumentId: UUID
    ): List<DocumentValidationResponse> {
        val documentValidationResults = mutableListOf<DocumentValidationResponse>()

        val hovedDokument = dokumentUnderArbeidRepository.getReferenceById(dokumentId)
        val vedlegg = getVedlegg(hovedDokument.id)

        (vedlegg + hovedDokument).forEach {
            if (it is DokumentUnderArbeidAsSmartdokument) {
                documentValidationResults += validateSingleSmartdocument(it)
            }
        }

        return documentValidationResults
    }

    private fun getVedlegg(hoveddokumentId: UUID): Set<DokumentUnderArbeidAsVedlegg> {
        return dokumentUnderArbeidCommonService.findVedleggByParentId(hoveddokumentId)
    }

    private fun validateSingleSmartdocument(dokument: DokumentUnderArbeidAsSmartdokument): DocumentValidationResponse {
        logger.debug("Getting json document, dokumentId: {}", dokument.id)
        val documentJson = smartEditorApiGateway.getDocumentAsJson(dokument.smartEditorId)
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

        if (hovedDokument !is DokumentUnderArbeidAsHoveddokument) {
            throw RuntimeException("document is not hoveddokument")
        }

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(hovedDokument.behandlingId)

        validateHoveddokumentBeforeFerdig(
            brevmottakerIdents = brevmottakerIdents,
            hovedDokument = hovedDokument,
            behandling = behandling,
        )
        val vedlegg = getVedlegg(hovedDokument.id)

        if (hovedDokument is SmartdokumentUnderArbeidAsHoveddokument && hovedDokument.isStaleSmartEditorDokument()) {
            mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(hovedDokument)
        }
        vedlegg.forEach {
            if (it is SmartdokumentUnderArbeidAsVedlegg && it.isStaleSmartEditorDokument()) {
                mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(it)
            } else if (it is JournalfoertDokumentUnderArbeidAsVedlegg) {
                val journalpostInDokarkiv =
                    safClient.getJournalpostAsSaksbehandler(it.journalpostId)

                it.name = getDokumentTitle(
                    journalpost = journalpostInDokarkiv,
                    dokumentInfoId = it.dokumentInfoId
                )
            }
        }

        val now = LocalDateTime.now()
        hovedDokument.markerFerdigHvisIkkeAlleredeMarkertFerdig(tidspunkt = now, saksbehandlerIdent = ident)
        val mapBrevmottakerIdentToBrevmottakerInput = kabalDocumentMapper.mapBrevmottakerIdentToBrevmottakerInput(
            behandling = behandling,
            brevmottakerIdents = brevmottakerIdents,
            dokumentType = hovedDokument.dokumentType!!
        )
        hovedDokument.brevmottakerIdents = mapBrevmottakerIdentToBrevmottakerInput.map {
            it.partId.value
        }.toSet()

        vedlegg.forEach { it.markerFerdigHvisIkkeAlleredeMarkertFerdig(tidspunkt = now, saksbehandlerIdent = ident) }

        /*
                //Don't create innholdsfortegnelse yet

                if (vedlegg.size > 1) {
                    innholdsfortegnelseService.saveInnholdsfortegnelse(
                        dokumentId,
                        mapBrevmottakerIdentToBrevmottakerInput.map { it.navn })
                }
        */

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

    private fun validateHoveddokumentBeforeFerdig(
        brevmottakerIdents: Set<String>?,
        hovedDokument: DokumentUnderArbeid,
        behandling: Behandling,
    ) {
        if (hovedDokument !is DokumentUnderArbeidAsHoveddokument) {
            throw DokumentValidationException("Kan ikke markere et vedlegg som ferdig")
        }

        if (hovedDokument.erMarkertFerdig() || hovedDokument.erFerdigstilt()) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på et dokument som er ferdigstilt")
        }

        val documentValidationErrors = validateIfSmartDokument(hovedDokument.id)
        if (documentValidationErrors.any { it.errors.isNotEmpty() }) {
            throw JsonToPdfValidationException(
                msg = "Validation error from json to pdf",
                errors = documentValidationErrors
            )
        }

        val invalidProperties = mutableListOf<InvalidProperty>()

        if (hovedDokument.dokumentType != DokumentType.NOTAT && brevmottakerIdents.isNullOrEmpty()) {
            throw DokumentValidationException("Brevmottakere må være satt")
        }

        val mottakere = kabalDocumentMapper.mapBrevmottakerIdentToBrevmottakerInput(
            behandling = behandling,
            brevmottakerIdents = brevmottakerIdents,
            dokumentType = hovedDokument.dokumentType!!
        )

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

    fun getInnholdsfortegnelseAsFysiskDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        hoveddokumentId: UUID,
        innloggetIdent: String
    ): FysiskDokument {
        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        val title = "Innholdsfortegnelse"

        return FysiskDokument(
            title = title,
            content = dokumentService.changeTitleInPDF(
                documentBytes = innholdsfortegnelseService.getInnholdsfortegnelseAsPdf(hoveddokumentId),
                title = title
            ),
            contentType = MediaType.APPLICATION_PDF
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
        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(dokument.behandlingId)

        val (content, title) = when (dokument) {
            is OpplastetDokumentUnderArbeidAsHoveddokument -> {
                mellomlagerService.getUploadedDocument(dokument.mellomlagerId!!) to dokument.name
            }

            is OpplastetDokumentUnderArbeidAsVedlegg -> {
                mellomlagerService.getUploadedDocument(dokument.mellomlagerId!!) to dokument.name
            }

            is DokumentUnderArbeidAsSmartdokument -> {
                if (dokument.isStaleSmartEditorDokument()) {
                    mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(dokument).bytes to dokument.name
                } else mellomlagerService.getUploadedDocument(dokument.mellomlagerId!!) to dokument.name
            }

            is JournalfoertDokumentUnderArbeidAsVedlegg -> {
                val fysiskDokument = dokumentService.getFysiskDokument(
                    journalpostId = dokument.journalpostId,
                    dokumentInfoId = dokument.dokumentInfoId,
                )
                fysiskDokument.content to fysiskDokument.title
            }

            else -> {
                error("can't come here")
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
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(
            behandlingId = document.behandlingId,
        )

        //first vedlegg
        dokumentUnderArbeidCommonService.findVedleggByParentId(dokumentId)
            .map {
                if (it.erMarkertFerdig()) {
                    throw MissingTilgangException("Attempting to delete finalized document ${document.id}")
                }
                it
            }
            .forEach { dokumentUnderArbeid ->
                slettEnkeltdokument(
                    document = dokumentUnderArbeid,
                    innloggetIdent = innloggetIdent,
                    behandlingRole = behandling.getRoleInBehandling(innloggetIdent),
                    behandling = behandling,
                )
            }

        if (document.erMarkertFerdig()) {
            throw MissingTilgangException("Attempting to delete finalized document ${document.id}")
        }

        //then hoveddokument
        slettEnkeltdokument(
            document = document,
            innloggetIdent = innloggetIdent,
            behandlingRole = behandling.getRoleInBehandling(innloggetIdent),
            behandling = behandling,
        )

    }

    private fun slettEnkeltdokument(
        document: DokumentUnderArbeid,
        innloggetIdent: String,
        behandlingRole: BehandlingRole,
        behandling: Behandling,
    ) {
        if (document.creatorRole != behandlingRole) {
            throw MissingTilgangException("$behandlingRole har ikke anledning til å slette dokumentet eiet av ${document.creatorRole}.")
        }

        if (document is DokumentUnderArbeidAsMellomlagret) {
            try {
                if (document.mellomlagerId != null) {
                    mellomlagerService.deleteDocument(document.mellomlagerId!!)
                }
            } catch (e: Exception) {
                logger.warn("Couldn't delete mellomlager document", e)
            }
        }

        if (document is DokumentUnderArbeidAsSmartdokument) {
            try {
                smartEditorApiGateway.deleteDocument(document.smartEditorId)
            } catch (e: Exception) {
                logger.warn("Couldn't delete smartEditor document", e)
            }
        }

        dokumentUnderArbeidRepository.delete(document)

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_SLETTET,
            fraVerdi = document.modified.toString(),
            tilVerdi = null,
            tidspunkt = LocalDateTime.now(),
            dokumentId = document.id,
        )
    }

    fun setAsVedlegg(
        parentId: UUID,
        dokumentId: UUID,
        innloggetIdent: String
    ): Pair<List<DokumentUnderArbeid>, List<DokumentUnderArbeid>> {
        if (parentId == dokumentId) {
            throw DokumentValidationException("Kan ikke gjøre et dokument til vedlegg for seg selv.")
        }
        val parentDocument = dokumentUnderArbeidRepository.getReferenceById(parentId)

        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(
            behandlingId = parentDocument.behandlingId,
        )

        if (parentDocument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble til et dokument som er ferdigstilt")
        }

        val descendants = getVedlegg(hoveddokumentId = dokumentId)

        val dokumentIdSet = mutableSetOf(dokumentId)
        dokumentIdSet += descendants.map { it.id }

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
        var dokumentUnderArbeid: DokumentUnderArbeid =
            dokumentUnderArbeidRepository.getReferenceById(currentDokumentId)

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble et dokument som er ferdigstilt")
        }

        return if (dokumentUnderArbeid is JournalfoertDokumentUnderArbeidAsVedlegg) {
            if (journalfoertDokumentUnderArbeidRepository.findByParentIdAndJournalpostIdAndDokumentInfoIdAndIdNot(
                    parentId = parentId,
                    journalpostId = dokumentUnderArbeid.journalpostId,
                    dokumentInfoId = dokumentUnderArbeid.dokumentInfoId,
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
            when (dokumentUnderArbeid) {
                is SmartdokumentUnderArbeidAsHoveddokument -> {
                    smartDokumentUnderArbeidAsHoveddokumentRepository.delete(dokumentUnderArbeid)
                    dokumentUnderArbeid = smartDokumentUnderArbeidAsVedleggRepository.save(
                        dokumentUnderArbeid.asVedlegg(parentId = parentId)
                    )
                }

                is OpplastetDokumentUnderArbeidAsHoveddokument -> {
                    opplastetDokumentUnderArbeidAsHoveddokumentRepository.delete(dokumentUnderArbeid)
                    dokumentUnderArbeid = opplastetDokumentUnderArbeidAsVedleggRepository.save(
                        dokumentUnderArbeid.asVedlegg(parentId = parentId)
                    )
                }

                is DokumentUnderArbeidAsVedlegg -> {
                    dokumentUnderArbeid.parentId = parentId
                }
            }
            dokumentUnderArbeid to null
        }
    }

    fun setAsHoveddokument(
        behandlingId: UUID,
        dokumentId: UUID,
        innloggetIdent: String
    ): DokumentUnderArbeid {
        val vedlegg = dokumentUnderArbeidRepository.getReferenceById(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(
            behandlingId = vedlegg.behandlingId,
        )
        //TODO: Skal det lages endringslogg på dette??

        if (vedlegg.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke frikoble et dokument som er ferdigstilt")
        }

        val savedDocument = when (vedlegg) {
            is OpplastetDokumentUnderArbeidAsVedlegg -> {
                //delete first so we can reuse the id
                opplastetDokumentUnderArbeidAsVedleggRepository.delete(vedlegg)

                opplastetDokumentUnderArbeidAsHoveddokumentRepository.save(
                    vedlegg.asHoveddokument()
                )
            }

            is SmartdokumentUnderArbeidAsVedlegg -> {
                //delete first so we can reuse the id
                smartDokumentUnderArbeidAsVedleggRepository.delete(vedlegg)

                smartDokumentUnderArbeidAsHoveddokumentRepository.save(
                    vedlegg.asHoveddokument()
                )
            }

            else -> {
                error("Document could not be set as hoveddokument")
            }
        }

        return savedDocument
    }

    fun findDokumenterNotFinished(behandlingId: UUID, checkReadAccess: Boolean = true): List<DokumentUnderArbeid> {
        //Sjekker tilgang på behandlingsnivå:
        if (checkReadAccess) {
            behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        }

        return dokumentUnderArbeidRepository.findByBehandlingIdAndFerdigstiltIsNull(behandlingId)
    }

    fun getSmartDokumenterUnderArbeid(behandlingId: UUID, ident: String): List<DokumentUnderArbeid> {
        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        val hoveddokumenter =
            smartDokumentUnderArbeidAsHoveddokumentRepository.findByBehandlingIdAndMarkertFerdigIsNull(
                behandlingId
            )

        val vedlegg = smartDokumentUnderArbeidAsVedleggRepository.findByBehandlingIdAndMarkertFerdigIsNull(
            behandlingId
        )

        val duaList = mutableListOf<DokumentUnderArbeid>()
        duaList += hoveddokumenter
        duaList += vedlegg

        return duaList.sortedBy { it.created }
    }

    fun opprettDokumentEnhet(hovedDokumentId: UUID): DokumentUnderArbeidAsHoveddokument {
        logger.debug("opprettDokumentEnhet hoveddokument with id {}", hovedDokumentId)
        val hovedDokument =
            dokumentUnderArbeidRepository.getReferenceById(hovedDokumentId) as DokumentUnderArbeidAsHoveddokument
        logger.debug("got hoveddokument with id {}, dokmentEnhetId {}", hovedDokumentId, hovedDokument.dokumentEnhetId)
        val vedlegg = dokumentUnderArbeidCommonService.findVedleggByParentId(hovedDokument.id)
        logger.debug("got vedlegg for hoveddokument id {}, size: {}", hovedDokumentId, vedlegg.size)
        //Denne er alltid sann
        if (hovedDokument.dokumentEnhetId == null) {
            logger.debug("hoveddokument.dokumentEnhetId == null, id {}", hovedDokumentId)
            //Vi vet at smartEditor-dokumentene har en oppdatert snapshot i mellomlageret fordi det ble fikset i finnOgMarkerFerdigHovedDokument
            val behandling = behandlingService.getBehandlingForUpdateBySystembruker(hovedDokument.behandlingId)
            val dokumentEnhetId = kabalDocumentGateway.createKomplettDokumentEnhet(
                behandling = behandling,
                hovedDokument = hovedDokument,
                vedlegg = vedlegg,
                innholdsfortegnelse = innholdsfortegnelseService.getInnholdsfortegnelse(hovedDokumentId)
            )
            logger.debug("got dokumentEnhetId {} for hoveddokumentid {}", dokumentEnhetId, hovedDokumentId)
            hovedDokument.dokumentEnhetId = dokumentEnhetId
            logger.debug("wrote dokumentEnhetId {} for hoveddokumentid {}", dokumentEnhetId, hovedDokumentId)
        }
        return hovedDokument
    }

    fun ferdigstillDokumentEnhet(hovedDokumentId: UUID): DokumentUnderArbeidAsHoveddokument {
        logger.debug("ferdigstillDokumentEnhet hoveddokument with id {}", hovedDokumentId)
        val hovedDokument =
            dokumentUnderArbeidRepository.getReferenceById(hovedDokumentId) as DokumentUnderArbeidAsHoveddokument
        val vedlegg = dokumentUnderArbeidCommonService.findVedleggByParentId(hovedDokument.id)
        val behandling: Behandling = behandlingService.getBehandlingForUpdateBySystembruker(hovedDokument.behandlingId)

        logger.debug(
            "calling fullfoerDokumentEnhet ({}) for hoveddokument with id {}",
            hovedDokument.dokumentEnhetId,
            hovedDokumentId
        )
        val dokumentEnhetFullfoerOutput =
            kabalDocumentGateway.fullfoerDokumentEnhet(dokumentEnhetId = hovedDokument.dokumentEnhetId!!)

        val journalpostIdSet = dokumentEnhetFullfoerOutput.sourceReferenceWithJoarkReferencesList.flatMap {
            it.joarkReferenceList.map { joarkReference ->
                joarkReference.journalpostId
            }
        }.toSet()

        journalpostIdSet.forEach { documentInfo ->
            val journalpost = safClient.getJournalpostAsSystembruker(documentInfo)
            val saksbehandlerIdent = SYSTEMBRUKER
            val saksdokumenter = journalpost.mapToSaksdokumenter()

            saksdokumenter.forEach { saksdokument ->
                behandling.addSaksdokument(saksdokument, saksbehandlerIdent)
                    ?.also { applicationEventPublisher.publishEvent(it) }
            }
        }

        dokumentEnhetFullfoerOutput.sourceReferenceWithJoarkReferencesList.forEach { sourceReferenceWithJoarkReferences ->
            val currentDokumentUnderArbeid =
                dokumentUnderArbeidRepository.getReferenceById(sourceReferenceWithJoarkReferences.sourceReference!!)
            sourceReferenceWithJoarkReferences.joarkReferenceList.forEach { joarkReference ->
                currentDokumentUnderArbeid.dokarkivReferences.add(
                    DokumentUnderArbeidDokarkivReference(
                        journalpostId = joarkReference.journalpostId,
                        dokumentInfoId = joarkReference.dokumentInfoId,
                    )
                )
            }
        }

        val now = LocalDateTime.now()

        logger.debug(
            "about to call hovedDokument.ferdigstillHvisIkkeAlleredeFerdigstilt(now) for dokument with id {}",
            hovedDokumentId
        )
        hovedDokument.ferdigstillHvisIkkeAlleredeFerdigstilt(now)
        if (hovedDokument is DokumentUnderArbeidAsSmartdokument) {
            try {
                smartEditorApiGateway.deleteDocumentAsSystemUser(hovedDokument.smartEditorId)
            } catch (e: Exception) {
                logger.warn("Couldn't delete hoveddokument from smartEditorApi", e)
            }
        }

        logger.debug(
            "about to call ferdigstillHvisIkkeAlleredeFerdigstilt(now) for vedlegg of dokument with id {}",
            hovedDokumentId
        )
        vedlegg.forEach {
            it.ferdigstillHvisIkkeAlleredeFerdigstilt(now)
            if (it is DokumentUnderArbeidAsSmartdokument) {
                try {
                    smartEditorApiGateway.deleteDocumentAsSystemUser(it.smartEditorId)
                } catch (e: Exception) {
                    logger.warn("Couldn't delete vedlegg from smartEditorApi", e)
                }
            }
        }
        logger.debug("about to return hoveddokument for hoveddokumentId {}", hovedDokumentId)
        return hovedDokument
    }

    fun getSmartEditorId(dokumentId: UUID, readOnly: Boolean): UUID {
        val dokumentUnderArbeid = dokumentUnderArbeidRepository.getReferenceById(dokumentId)
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()

        if (dokumentUnderArbeid !is DokumentUnderArbeidAsSmartdokument) {
            throw RuntimeException("dokument is not smartdokument")
        }

        //Sjekker tilgang på behandlingsnivå:
        if (readOnly) {
            behandlingService.getBehandlingAndCheckLeseTilgangForPerson(dokumentUnderArbeid.behandlingId)
        } else {
            behandlingService.getBehandlingForWriteAllowROLAndMU(
                behandlingId = dokumentUnderArbeid.behandlingId,
                utfoerendeSaksbehandlerIdent = ident,
            )
        }

        return dokumentUnderArbeid.smartEditorId
    }

    private fun mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(dokument: DokumentUnderArbeid): PDFDocument {

        if (dokument !is DokumentUnderArbeidAsSmartdokument) {
            throw RuntimeException("dokument is not smartdokument")
        }

        val documentJson = smartEditorApiGateway.getDocumentAsJson(dokument.smartEditorId)
        val pdfDocument = kabalJsonToPdfClient.getPDFDocument(documentJson)

        val mellomlagerId =
            mellomlagerService.uploadByteArray(
                tittel = dokument.name,
                content = pdfDocument.bytes
            )

        if (dokument.mellomlagerId != null) {
            mellomlagerService.deleteDocument(dokument.mellomlagerId!!)
        }

        val now = LocalDateTime.now()
        dokument.mellomlagerId = mellomlagerId
        dokument.mellomlagretDate = now
        dokument.size = pdfDocument.bytes.size.toLong()
        dokument.modified = now

        return pdfDocument
    }

    private fun DokumentUnderArbeidAsSmartdokument.isStaleSmartEditorDokument() =
        smartEditorApiGateway.isMellomlagretDokumentStale(
            mellomlagretDate = this.mellomlagretDate,
            smartEditorId = this.smartEditorId,
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


