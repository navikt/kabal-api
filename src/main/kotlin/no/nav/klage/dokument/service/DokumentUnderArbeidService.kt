package no.nav.klage.dokument.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import jakarta.transaction.Transactional
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.*
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.dokument.clients.kabaljsontopdf.KabalJsonToPdfClient
import no.nav.klage.dokument.clients.kabalsmarteditorapi.KabalSmartEditorApiClient
import no.nav.klage.dokument.clients.kabalsmarteditorapi.model.response.SmartDocumentResponse
import no.nav.klage.dokument.domain.FysiskDokument
import no.nav.klage.dokument.domain.PDFDocument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.*
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.exceptions.JsonToPdfValidationException
import no.nav.klage.dokument.gateway.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.repositories.*
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Template
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.clients.saf.graphql.Journalpost
import no.nav.klage.oppgave.clients.saf.graphql.Journalstatus
import no.nav.klage.oppgave.config.getHistogram
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.events.DokumentFerdigstiltAvSaksbehandler
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.addSaksdokument
import no.nav.klage.oppgave.exceptions.InvalidProperty
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.exceptions.SectionedValidationErrorWithDetailsException
import no.nav.klage.oppgave.exceptions.ValidationSection
import no.nav.klage.oppgave.service.*
import no.nav.klage.oppgave.util.*
import org.hibernate.Hibernate
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.time.LocalDate
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
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val dokumentService: DokumentService,
    private val eregClient: EregClient,
    private val innholdsfortegnelseService: InnholdsfortegnelseService,
    private val safFacade: SafFacade,
    private val dokumentMapper: DokumentMapper,
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val saksbehandlerService: SaksbehandlerService,
    private val kabalSmartEditorApiClient: KabalSmartEditorApiClient,
    private val partSearchService: PartSearchService,
    meterRegistry: MeterRegistry,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val kodeverkService: KodeverkService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        private val objectMapper: ObjectMapper = ourJacksonObjectMapper()
    }

    private val metricForSmartDocumentVersions = meterRegistry.getHistogram(
        name = "smartDocument.versions",
        baseUnit = "versions",
    )

    fun createOpplastetDokumentUnderArbeid(
        behandlingId: UUID,
        dokumentType: DokumentType,
        opplastetFil: FysiskDokument?,
        innloggetIdent: String,
        tittel: String,
        parentId: UUID?,
    ): DokumentView {
        //Sjekker lesetilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        if (!dokumentType.isInngaaende() || !innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter()) {
            if (behandling.avsluttetAvSaksbehandler == null) {
                validateCanCreateDocuments(
                    behandlingRole = behandlingRole,
                    parentDocument = if (parentId != null) dokumentUnderArbeidRepository.findById(parentId)
                        .get() as DokumentUnderArbeidAsHoveddokument else null
                )
            }
        }

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
                    datoMottatt = null,
                    journalfoerendeEnhetId = null,
                    inngaaendeKanal = null,
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
                )
            )
        }
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_OPPLASTET,
            fraVerdi = null,
            tilVerdi = document.created.toString(),
            tidspunkt = document.created,
        )

        val dokumentView = dokumentMapper.mapToDokumentView(
            dokumentUnderArbeid = document,
            journalpost = null,
            smartEditorDocument = null,
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

    fun kobleEllerFrikobleVedlegg(
        behandlingId: UUID,
        persistentDokumentId: UUID,
        optionalParentInput: OptionalPersistentDokumentIdInput,
    ): DokumentViewWithList {
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId = behandlingId)

        val (dokumentUnderArbeidList, duplicateJournalfoerteDokumenter) = if (optionalParentInput.dokumentId == null) {
            listOf(
                setAsHoveddokument(
                    behandlingId = behandlingId,
                    dokumentId = persistentDokumentId,
                    innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
                )
            ) to emptyList()
        } else {
            setAsVedlegg(
                newParentId = optionalParentInput.dokumentId,
                dokumentId = persistentDokumentId,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        }

        val journalpostIdSet = dokumentUnderArbeidList.plus(duplicateJournalfoerteDokumenter)
            .filterIsInstance<JournalfoertDokumentUnderArbeidAsVedlegg>()
            .map { it.journalpostId }.toSet()

        val journalpostListForUser = safFacade.getJournalposter(
            journalpostIdSet = journalpostIdSet,
            fnr = behandling.sakenGjelder.partId.value,
            saksbehandlerContext = true,
        )

        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                DocumentsChangedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    documents = dokumentUnderArbeidList.map {
                        DocumentsChangedEvent.DocumentChanged(
                            id = it.id.toString(),
                            parentId = if (it is DokumentUnderArbeidAsVedlegg) it.parentId.toString() else null,
                            dokumentTypeId = getDokumentTypeIdFromThisOrParent(it),
                            tittel = it.name,
                            isMarkertAvsluttet = it.erMarkertFerdig(),
                        )
                    }
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.DOCUMENTS_CHANGED,
        )

        return dokumentMapper.mapToDokumentListView(
            dokumentUnderArbeidList = dokumentUnderArbeidList,
            duplicateJournalfoerteDokumenter = duplicateJournalfoerteDokumenter,
            journalpostList = journalpostListForUser,
        )
    }

    private fun getDokumentTypeIdFromThisOrParent(dokumentUnderArbeid: DokumentUnderArbeid): String? {
        return if (dokumentUnderArbeid is DokumentUnderArbeidAsHoveddokument) {
            dokumentUnderArbeid.dokumentType.id
        } else {
            dokumentUnderArbeid as DokumentUnderArbeidAsVedlegg
            val parentDocument = dokumentUnderArbeidRepository.findById(dokumentUnderArbeid.parentId).get()
            parentDocument as DokumentUnderArbeidAsHoveddokument
            parentDocument.dokumentType.id
        }
    }

    fun opprettSmartdokument(
        behandlingId: UUID,
        dokumentType: DokumentType,
        json: String?,
        smartEditorTemplateId: String,
        innloggetIdent: String,
        tittel: String,
        parentId: UUID?,
    ): DokumentView {
        //Sjekker lesetilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        if (behandling.avsluttetAvSaksbehandler == null) {
            validateCanCreateDocuments(
                behandlingRole = behandlingRole,
                parentDocument = if (parentId != null) dokumentUnderArbeidRepository.findById(parentId)
                    .get() as DokumentUnderArbeidAsHoveddokument else null
            )
        }

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
                    journalfoerendeEnhetId = null,
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

        val smartEditorDocument = kabalSmartEditorApiClient.getDocument(documentId = document.smartEditorId)

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

    fun addJournalfoerteDokumenterAsVedlegg(
        behandlingId: UUID,
        journalfoerteDokumenterInput: JournalfoerteDokumenterInput,
        innloggetIdent: String
    ): JournalfoerteDokumenterResponse {
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        val parentDocument =
            dokumentUnderArbeidRepository.findById(journalfoerteDokumenterInput.parentId)
                .get() as DokumentUnderArbeidAsHoveddokument

        if (parentDocument.isInngaaende()) {
            throw DokumentValidationException("Kan ikke sette journalførte dokumenter som vedlegg til ${parentDocument.dokumentType.navn}.")
        }

        val journalpostListForUser = safFacade.getJournalposter(
            journalpostIdSet = journalfoerteDokumenterInput.journalfoerteDokumenter.map { it.journalpostId }.toSet(),
            fnr = behandling.sakenGjelder.partId.value,
            saksbehandlerContext = true,
        )

        if (journalpostListForUser.any { it.journalstatus == Journalstatus.MOTTATT }) {
            throw DokumentValidationException("Kan ikke legge til journalførte dokumenter med status 'Mottatt' som vedlegg. Fullfør journalføring i Gosys for å gjøre dette.")
        }

        val (added, duplicates) = createJournalfoerteDokumenter(
            parentId = journalfoerteDokumenterInput.parentId,
            journalfoerteDokumenter = journalfoerteDokumenterInput.journalfoerteDokumenter,
            behandling = behandling,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            journalpostListForUser = journalpostListForUser
        )

        val addedJournalfoerteDokumenter = getDokumentViewListForJournalfoertDokumentUnderArbeidAsVedleggList(
            dokumentUnderArbeidList = added,
            behandling = behandling,
            journalpostList = journalpostListForUser,
        )

        if (addedJournalfoerteDokumenter.isNotEmpty()) {
            publishInternalEvent(
                data = objectMapper.writeValueAsString(
                    DocumentsAddedEvent(
                        actor = Employee(
                            navIdent = innloggetIdent,
                            navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                        ),
                        timestamp = LocalDateTime.now(),
                        documents = addedJournalfoerteDokumenter,
                    )
                ),
                behandlingId = behandling.id,
                type = InternalEventType.DOCUMENTS_ADDED,
            )
        }

        return JournalfoerteDokumenterResponse(
            addedJournalfoerteDokumenter = addedJournalfoerteDokumenter,
            duplicateJournalfoerteDokumenter = duplicates,
        )
    }

    fun getDokumentViewListForJournalfoertDokumentUnderArbeidAsVedleggList(
        dokumentUnderArbeidList: List<JournalfoertDokumentUnderArbeidAsVedlegg>,
        behandling: Behandling,
        journalpostList: List<Journalpost>
    ): List<DokumentView> {
        return dokumentUnderArbeidList.sortedByDescending { it.sortKey }
            .map { journalfoertVedlegg ->
                dokumentMapper.mapToDokumentView(
                    dokumentUnderArbeid = journalfoertVedlegg,
                    journalpost = journalpostList.find { it.journalpostId == journalfoertVedlegg.journalpostId }!!,
                    smartEditorDocument = null,
                    behandling = behandling,
                )
            }
    }

    fun createJournalfoerteDokumenter(
        parentId: UUID,
        journalfoerteDokumenter: Set<JournalfoertDokumentReference>,
        behandling: Behandling,
        innloggetIdent: String,
        journalpostListForUser: List<Journalpost>,
    ): Pair<List<JournalfoertDokumentUnderArbeidAsVedlegg>, List<JournalfoertDokumentReference>> {
        val parentDocument =
            dokumentUnderArbeidRepository.findById(parentId).get() as DokumentUnderArbeidAsHoveddokument

        if (parentDocument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble til et dokument som er ferdigstilt")
        }

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        if (behandling.avsluttetAvSaksbehandler == null) {
            val isCurrentROL = behandling.rolIdent == innloggetIdent

            validateCanCreateDocuments(
                behandlingRole = behandlingRole,
                parentDocument = parentDocument
            )

            behandlingService.connectDocumentsToBehandling(
                behandlingId = behandling.id,
                journalfoertDokumentReferenceSet = journalfoerteDokumenter,
                saksbehandlerIdent = innloggetIdent,
                systemUserContext = false,
                ignoreCheckSkrivetilgang = isCurrentROL
            )
        }

        val alreadyAddedDocuments =
            journalfoertDokumentUnderArbeidRepository.findByParentId(parentId)

        val alreadAddedDocumentsMapped = alreadyAddedDocuments.map {
            JournalfoertDokumentReference(
                journalpostId = it.journalpostId,
                dokumentInfoId = it.dokumentInfoId
            )
        }.toSet()

        val (toAdd, duplicates) = journalfoerteDokumenter.partition { it !in alreadAddedDocumentsMapped }

        val now = LocalDateTime.now()

        val resultingDocuments = toAdd.map { journalfoertDokumentReference ->
            val journalpostInDokarkiv =
                journalpostListForUser.find { it.journalpostId == journalfoertDokumentReference.journalpostId }!!

            val document = JournalfoertDokumentUnderArbeidAsVedlegg(
                name = getDokumentTitle(
                    journalpost = journalpostInDokarkiv,
                    dokumentInfoId = journalfoertDokumentReference.dokumentInfoId
                ),
                behandlingId = behandling.id,
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
                sortKey = getSortKey(
                    journalpost = journalpostInDokarkiv,
                    dokumentInfoId = journalfoertDokumentReference.dokumentInfoId
                )
            )

            journalfoertDokumentUnderArbeidRepository.save(
                document
            )
        }

        val resultingIdList = alreadyAddedDocuments.map { it.id }.union(resultingDocuments.map { it.id })

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.JOURNALFOERT_DOKUMENT_UNDER_ARBEID_OPPRETTET,
            fraVerdi = alreadyAddedDocuments.joinToString { it.id.toString() },
            tilVerdi = resultingIdList.joinToString(),
            tidspunkt = now,
        )

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

    private fun validateCanCreateDocuments(
        behandlingRole: BehandlingRole,
        parentDocument: DokumentUnderArbeidAsHoveddokument?
    ) {
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

    fun getDokumentUnderArbeid(dokumentId: UUID) = dokumentUnderArbeidRepository.findById(dokumentId).get()

    fun getMappedDokumentUnderArbeid(
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

    fun updateDokumentType(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        newDokumentType: DokumentType,
        innloggetIdent: String
    ): DokumentUnderArbeidAsHoveddokument {
        val dokumentUnderArbeid = dokumentUnderArbeidRepository.findById(dokumentId).get()

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        if (dokumentUnderArbeid !is DokumentUnderArbeidAsHoveddokument) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på vedlegg")
        }

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på et dokument som er ferdigstilt")
        }

        val vedlegg = getVedlegg(dokumentId)

        if (newDokumentType.isInngaaende()) {
            if (vedlegg.any { it !is OpplastetDokumentUnderArbeidAsVedlegg }) {
                throw DokumentValidationException("${newDokumentType.navn} kan kun ha opplastede vedlegg. Fjern ugyldige vedlegg og prøv på nytt.")
            }
        }

        updateDokumentTypeInSingleDUA(
            dokumentUnderArbeid = dokumentUnderArbeid,
            newDokumentType = newDokumentType,
            behandling = behandling,
            innloggetIdent = innloggetIdent
        )

        return dokumentUnderArbeid
    }

    private fun updateDokumentTypeInSingleDUA(
        dokumentUnderArbeid: DokumentUnderArbeidAsHoveddokument,
        newDokumentType: DokumentType,
        behandling: Behandling,
        innloggetIdent: String
    ) {
        val previousValue = dokumentUnderArbeid.dokumentType
        dokumentUnderArbeid.dokumentType = newDokumentType

        dokumentUnderArbeid.modified = LocalDateTime.now()

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_TYPE,
            fraVerdi = previousValue.id,
            tilVerdi = dokumentUnderArbeid.dokumentType.toString(),
            tidspunkt = dokumentUnderArbeid.modified,
        )

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                DocumentsChangedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    documents = listOf(
                        DocumentsChangedEvent.DocumentChanged(
                            id = dokumentUnderArbeid.id.toString(),
                            parentId = null,
                            dokumentTypeId = dokumentUnderArbeid.dokumentType.id,
                            tittel = dokumentUnderArbeid.name,
                            isMarkertAvsluttet = dokumentUnderArbeid.erMarkertFerdig(),
                        )
                    ),
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.DOCUMENTS_CHANGED,
        )
    }

    fun updateDatoMottatt(
        behandlingId: UUID,
        dokumentId: UUID,
        datoMottatt: LocalDate,
        innloggetIdent: String
    ): DokumentUnderArbeidAsHoveddokument {
        val dokumentUnderArbeid =
            dokumentUnderArbeidRepository.findById(dokumentId).get() as OpplastetDokumentUnderArbeidAsHoveddokument

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke sette dato mottatt på et dokument som er ferdigstilt")
        }

        if (datoMottatt.isAfter(LocalDate.now())) {
            throw DokumentValidationException("Kan ikke sette dato mottatt i fremtiden")
        }

        if (!dokumentUnderArbeid.isInngaaende()) {
            throw DokumentValidationException("Kan bare sette dato mottatt på inngående dokument.")
        }

        val previousValue = dokumentUnderArbeid.datoMottatt
        dokumentUnderArbeid.datoMottatt = datoMottatt

        dokumentUnderArbeid.modified = LocalDateTime.now()

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_DATO_MOTTATT,
            fraVerdi = previousValue.toString(),
            tilVerdi = dokumentUnderArbeid.datoMottatt.toString(),
            tidspunkt = dokumentUnderArbeid.modified,
        )

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                DocumentsChangedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    documents = listOf(
                        DocumentsChangedEvent.DocumentChanged(
                            id = dokumentUnderArbeid.id.toString(),
                            parentId = null,
                            dokumentTypeId = dokumentUnderArbeid.dokumentType.id,
                            tittel = dokumentUnderArbeid.name,
                            isMarkertAvsluttet = dokumentUnderArbeid.erMarkertFerdig(),
                        )
                    ),
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.DOCUMENTS_CHANGED,
        )

        return dokumentUnderArbeid
    }

    fun updateInngaaendeKanal(
        behandlingId: UUID,
        dokumentId: UUID,
        inngaaendeKanal: InngaaendeKanal,
        innloggetIdent: String
    ): DokumentUnderArbeidAsHoveddokument {
        val dokumentUnderArbeid =
            dokumentUnderArbeidRepository.findById(dokumentId).get() as OpplastetDokumentUnderArbeidAsHoveddokument

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke sette inngående kanal på et dokument som er ferdigstilt")
        }

        if (dokumentUnderArbeid.dokumentType != DokumentType.ANNEN_INNGAAENDE_POST) {
            throw DokumentValidationException("Kan bare sette inngående kanal på ${DokumentType.ANNEN_INNGAAENDE_POST.navn}.")
        }

        val previousValue = dokumentUnderArbeid.inngaaendeKanal
        dokumentUnderArbeid.inngaaendeKanal = inngaaendeKanal

        dokumentUnderArbeid.modified = LocalDateTime.now()

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_INNGAAENDE_KANAL,
            fraVerdi = previousValue.toString(),
            tilVerdi = dokumentUnderArbeid.inngaaendeKanal.toString(),
            tidspunkt = dokumentUnderArbeid.modified,
        )

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                DocumentsChangedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    documents = listOf(
                        DocumentsChangedEvent.DocumentChanged(
                            id = dokumentUnderArbeid.id.toString(),
                            parentId = null,
                            dokumentTypeId = dokumentUnderArbeid.dokumentType.id,
                            tittel = dokumentUnderArbeid.name,
                            isMarkertAvsluttet = dokumentUnderArbeid.erMarkertFerdig(),
                        )
                    ),
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.DOCUMENTS_CHANGED,
        )

        return dokumentUnderArbeid
    }

    fun updateAvsender(
        behandlingId: UUID,
        dokumentId: UUID,
        avsenderInput: AvsenderInput,
        innloggetIdent: String
    ): OpplastetDokumentUnderArbeidAsHoveddokument {

        //Validate part
        partSearchService.searchPart(
            identifikator = avsenderInput.id,
            skipAccessControl = true
        )

        val dokumentUnderArbeid = dokumentUnderArbeidRepository.findById(dokumentId).get()

        if (dokumentUnderArbeid.isVedlegg()) {
            throw DokumentValidationException("Kan ikke sette avsender på vedlegg")
        }

        dokumentUnderArbeid as OpplastetDokumentUnderArbeidAsHoveddokument

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke sette avsender på et dokument som er ferdigstilt")
        }

        if (!dokumentUnderArbeid.isInngaaende()) {
            throw DokumentValidationException("Kan bare sette avsender på inngående dokument")
        }

        val previousValue = dokumentUnderArbeid.avsenderMottakerInfoSet.firstOrNull()
        dokumentUnderArbeid.avsenderMottakerInfoSet.clear()
        dokumentUnderArbeid.avsenderMottakerInfoSet.add(
            DokumentUnderArbeidAvsenderMottakerInfo(
                identifikator = avsenderInput.id,
                localPrint = false,
                forceCentralPrint = false,
                address = null,
            )
        )

        dokumentUnderArbeid.modified = LocalDateTime.now()

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_AVSENDER_MOTTAKER,
            fraVerdi = previousValue.toString(),
            tilVerdi = dokumentUnderArbeid.avsenderMottakerInfoSet.toString(),
            tidspunkt = dokumentUnderArbeid.modified,
        )

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                DocumentsChangedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    documents = listOf(
                        DocumentsChangedEvent.DocumentChanged(
                            id = dokumentUnderArbeid.id.toString(),
                            parentId = null,
                            dokumentTypeId = dokumentUnderArbeid.dokumentType.id,
                            tittel = dokumentUnderArbeid.name,
                            isMarkertAvsluttet = dokumentUnderArbeid.erMarkertFerdig(),
                        )
                    ),
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.DOCUMENTS_CHANGED,
        )

        return dokumentUnderArbeid
    }

    fun updateMottakere(
        behandlingId: UUID,
        dokumentId: UUID,
        mottakerInput: MottakerInput,
        innloggetIdent: String
    ): DokumentUnderArbeidAsHoveddokument {

        //Validate parts
        mottakerInput.mottakerList.forEach { mottaker ->
            partSearchService.searchPart(
                identifikator = mottaker.id,
                skipAccessControl = true
            )

            if (mottaker.overriddenAddress != null) {
                val landkoder = kodeverkService.getLandkoder()
                if (landkoder.find { it.landkode == mottaker.overriddenAddress.landkode } == null) {
                    throw DokumentValidationException("Ugyldig landkode: ${mottaker.overriddenAddress.landkode}")
                }

                if (mottaker.overriddenAddress.landkode == "NO") {
                    if (mottaker.overriddenAddress.postnummer == null) {
                        throw DokumentValidationException("Postnummer required for Norwegian address.")
                    }
                } else {
                    if (mottaker.overriddenAddress.adresselinje1 == null) {
                        throw DokumentValidationException("Adresselinje1 required for foreign address.")
                    }
                }
            }
        }

        val dokumentUnderArbeid = dokumentUnderArbeidRepository.findById(dokumentId).get()

        if (dokumentUnderArbeid.isVedlegg()) {
            throw DokumentValidationException("Kan ikke sette mottakere på vedlegg")
        }

        dokumentUnderArbeid as DokumentUnderArbeidAsHoveddokument

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke sette mottakere på et dokument som er ferdigstilt")
        }

        if (!dokumentUnderArbeid.isUtgaaende()) {
            throw DokumentValidationException("Kan bare sette mottakere på utgående dokument")
        }

        val previousValue = dokumentUnderArbeid.avsenderMottakerInfoSet

        dokumentUnderArbeid.avsenderMottakerInfoSet.clear()

        mottakerInput.mottakerList.forEach {
            val (markLocalPrint, forceCentralPrint) = when(it.handling) {
                HandlingEnum.AUTO -> false to false
                HandlingEnum.LOCAL_PRINT -> true to false
                HandlingEnum.CENTRAL_PRINT -> false to true
            }
            dokumentUnderArbeid.avsenderMottakerInfoSet.add(
                DokumentUnderArbeidAvsenderMottakerInfo(
                    identifikator = it.id,
                    localPrint = markLocalPrint,
                    forceCentralPrint = forceCentralPrint,
                    address = getDokumentUnderArbeidAdresse(it.overriddenAddress),
                )
            )
        }

        dokumentUnderArbeid.modified = LocalDateTime.now()

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_AVSENDER_MOTTAKER,
            fraVerdi = previousValue.toString(),
            tilVerdi = dokumentUnderArbeid.avsenderMottakerInfoSet.toString(),
            tidspunkt = dokumentUnderArbeid.modified,
        )

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                DocumentsChangedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    documents = listOf(
                        DocumentsChangedEvent.DocumentChanged(
                            id = dokumentUnderArbeid.id.toString(),
                            parentId = null,
                            dokumentTypeId = dokumentUnderArbeid.dokumentType.id,
                            tittel = dokumentUnderArbeid.name,
                            isMarkertAvsluttet = dokumentUnderArbeid.erMarkertFerdig(),
                        )
                    ),
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.DOCUMENTS_CHANGED,
        )

        return dokumentUnderArbeid
    }

    private fun getDokumentUnderArbeidAdresse(overrideAddress: AddressInput?): DokumentUnderArbeidAdresse? {
        return if (overrideAddress != null) {
            val poststed = if (overrideAddress.landkode == "NO") {
                if (overrideAddress.postnummer != null) {
                    kodeverkService.getPoststed(overrideAddress.postnummer)
                } else null
            } else null

            DokumentUnderArbeidAdresse(
                adresselinje1 = overrideAddress.adresselinje1,
                adresselinje2 = overrideAddress.adresselinje2,
                adresselinje3 = overrideAddress.adresselinje3,
                postnummer = overrideAddress.postnummer,
                poststed = poststed,
                landkode = overrideAddress.landkode
            )
        } else null
    }

    private fun DokumentUnderArbeid.isVedlegg(): Boolean {
        val duaUnproxied = Hibernate.unproxy(this)
        return duaUnproxied is SmartdokumentUnderArbeidAsVedlegg ||
                duaUnproxied is OpplastetDokumentUnderArbeidAsVedlegg ||
                duaUnproxied is JournalfoertDokumentUnderArbeidAsVedlegg
    }

    fun updateDokumentTitle(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        dokumentTitle: String,
        innloggetIdent: String
    ): DokumentUnderArbeid {
        val dokumentUnderArbeid = dokumentUnderArbeidRepository.findById(dokumentId).get()

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(dokumentUnderArbeid.behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        if (behandling.avsluttetAvSaksbehandler == null) {
            if (dokumentUnderArbeid.creatorRole != behandlingRole && !innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter()) {
                throw MissingTilgangException("$behandlingRole har ikke anledning til å endre tittel på dette dokumentet eiet av ${dokumentUnderArbeid.creatorRole}.")
            }
        }

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre tittel på et dokument som er ferdigstilt")
        }

        if (dokumentUnderArbeid is JournalfoertDokumentUnderArbeidAsVedlegg) {
            throw DokumentValidationException("Kan ikke endre tittel på journalført dokument i denne konteksten")
        }

        val oldValue = dokumentUnderArbeid.name
        dokumentUnderArbeid.name = dokumentTitle
        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_NAME,
            fraVerdi = oldValue,
            tilVerdi = dokumentUnderArbeid.name,
            tidspunkt = LocalDateTime.now(),
        )

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                DocumentsChangedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    documents = listOf(
                        DocumentsChangedEvent.DocumentChanged(
                            id = dokumentUnderArbeid.id.toString(),
                            parentId = if (dokumentUnderArbeid is DokumentUnderArbeidAsVedlegg) dokumentUnderArbeid.parentId.toString() else null,
                            dokumentTypeId = getDokumentTypeIdFromThisOrParent(dokumentUnderArbeid),
                            tittel = dokumentUnderArbeid.name,
                            isMarkertAvsluttet = dokumentUnderArbeid.erMarkertFerdig(),
                        )
                    ),
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.DOCUMENTS_CHANGED,
        )

        return dokumentUnderArbeid
    }

    fun validateDocument(
        dokumentId: UUID,
    ) {
        val dokument = dokumentUnderArbeidRepository.findById(dokumentId).get()
        if (dokument.erMarkertFerdig()) {
            throw DokumentValidationException("Dokument er allerede ferdigstilt.")
        }

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(dokument.behandlingId)

        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        if (behandling.avsluttetAvSaksbehandler == null) {
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
    }

    fun validateIfSmartDokument(
        dokumentId: UUID
    ): List<DocumentValidationResponse> {
        val documentValidationResults = mutableListOf<DocumentValidationResponse>()

        val hovedDokument = dokumentUnderArbeidRepository.findById(dokumentId).get()
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
        behandlingId: UUID,
        dokumentId: UUID,
        innloggetIdent: String,
        ferdigstillDokumentInput: FerdigstillDokumentInput?,
    ): DokumentUnderArbeidAsHoveddokument {
        val hovedDokument = dokumentUnderArbeidRepository.findById(dokumentId).get()

        if (hovedDokument !is DokumentUnderArbeidAsHoveddokument) {
            throw RuntimeException("document is not hoveddokument")
        }

        hovedDokument.journalfoerendeEnhetId = saksbehandlerService.getEnhetForSaksbehandler(
            innloggetIdent
        ).enhetId

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(hovedDokument.behandlingId)

        if (hovedDokument.dokumentType == DokumentType.KJENNELSE_FRA_TRYGDERETTEN) {
            hovedDokument.avsenderMottakerInfoSet.clear()
            hovedDokument.avsenderMottakerInfoSet.add(
                DokumentUnderArbeidAvsenderMottakerInfo(
                    //Hardkoder Trygderetten
                    identifikator = "974761084",
                    localPrint = false,
                    forceCentralPrint = false,
                    address = null,
                )
            )
        } else if (!ferdigstillDokumentInput?.brevmottakerIds.isNullOrEmpty()) {
            hovedDokument.avsenderMottakerInfoSet.clear()
            ferdigstillDokumentInput!!.brevmottakerIds!!.forEach {
                hovedDokument.avsenderMottakerInfoSet.add(
                    toDokumentUnderArbeidBrevmottakerInfo(
                        id = it,
                        dokumentType = hovedDokument.dokumentType,
                        behandling = behandling
                    )
                )
            }
        }

        validateHoveddokumentBeforeFerdig(
            hovedDokument = hovedDokument,
        )

        val vedlegg = getVedlegg(hovedDokument.id)

        if (hovedDokument is SmartdokumentUnderArbeidAsHoveddokument && hovedDokument.isStaleSmartEditorDokument()) {
            mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(
                dokument = hovedDokument,
            )
            try {
                metricForSmartDocumentVersions.record(
                    smartEditorApiGateway.getSmartDocumentResponse(
                        smartEditorId = hovedDokument.smartEditorId
                    ).version.toDouble()
                )
            } catch (e: Exception) {
                logger.warn("could not record metrics for smart document versions", e)
            }
        }

        vedlegg.forEach {
            if (it is SmartdokumentUnderArbeidAsVedlegg && it.isStaleSmartEditorDokument()) {
                mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(it)
            }
        }

        val now = LocalDateTime.now()
        hovedDokument.markerFerdigHvisIkkeAlleredeMarkertFerdig(tidspunkt = now, saksbehandlerIdent = innloggetIdent)

        vedlegg.forEach {
            it.markerFerdigHvisIkkeAlleredeMarkertFerdig(
                tidspunkt = now,
                saksbehandlerIdent = innloggetIdent
            )
        }

        if (vedlegg.isNotEmpty() && !hovedDokument.isInngaaende()) {
            innholdsfortegnelseService.saveInnholdsfortegnelse(
                dokumentUnderArbeidId = dokumentId,
                fnr = behandling.sakenGjelder.partId.value,
            )
        }

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_MARKERT_FERDIG,
            fraVerdi = null,
            tilVerdi = hovedDokument.markertFerdig.toString(),
            tidspunkt = LocalDateTime.now(),
        )

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_BREVMOTTAKER_IDENTS,
            fraVerdi = null,
            tilVerdi = hovedDokument.avsenderMottakerInfoSet.joinToString { it.identifikator },
            tidspunkt = LocalDateTime.now(),
        )

        applicationEventPublisher.publishEvent(DokumentFerdigstiltAvSaksbehandler(hovedDokument))

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                DocumentsChangedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    documents = vedlegg.map {
                        DocumentsChangedEvent.DocumentChanged(
                            id = it.id.toString(),
                            parentId = it.parentId.toString(),
                            dokumentTypeId = getDokumentTypeIdFromThisOrParent(it),
                            tittel = it.name,
                            isMarkertAvsluttet = it.erMarkertFerdig(),
                        )
                    } + DocumentsChangedEvent.DocumentChanged(
                        id = hovedDokument.id.toString(),
                        parentId = null,
                        dokumentTypeId = hovedDokument.dokumentType.id,
                        tittel = hovedDokument.name,
                        isMarkertAvsluttet = hovedDokument.erMarkertFerdig(),
                    )
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.DOCUMENTS_CHANGED,
        )

        return hovedDokument
    }

    private fun toDokumentUnderArbeidBrevmottakerInfo(
        id: String,
        dokumentType: DokumentType,
        behandling: Behandling,
    ): DokumentUnderArbeidAvsenderMottakerInfo {
        return if (dokumentType == DokumentType.NOTAT) {
            DokumentUnderArbeidAvsenderMottakerInfo(
                identifikator = behandling.sakenGjelder.partId.value,
                localPrint = false,
                forceCentralPrint = false,
                address = null,
            )
        } else {
            DokumentUnderArbeidAvsenderMottakerInfo(
                identifikator = id,
                localPrint = false,
                forceCentralPrint = false,
                address = null,
            )
        }
    }

    private fun validateHoveddokumentBeforeFerdig(
        hovedDokument: DokumentUnderArbeidAsHoveddokument,
    ) {
        if (hovedDokument.erMarkertFerdig() || hovedDokument.erFerdigstilt()) {
            throw DokumentValidationException("Kan ikke markere et dokument som allerede er ferdigstilt som ferdigstilt")
        }

        val documentValidationErrors = validateIfSmartDokument(hovedDokument.id)
        if (documentValidationErrors.any { it.errors.isNotEmpty() }) {
            throw JsonToPdfValidationException(
                msg = "Validation error from json to pdf",
                errors = documentValidationErrors
            )
        }

        val avsenderMottakerInfoSet = hovedDokument.avsenderMottakerInfoSet

        val invalidProperties = mutableListOf<InvalidProperty>()

        if (hovedDokument.dokumentType != DokumentType.NOTAT && avsenderMottakerInfoSet.isEmpty()) {
            throw DokumentValidationException("Avsender/mottakere må være satt")
        }

        if (hovedDokument.dokumentType == DokumentType.ANNEN_INNGAAENDE_POST && (hovedDokument as OpplastetDokumentUnderArbeidAsHoveddokument).inngaaendeKanal == null) {
            throw DokumentValidationException("Trenger spesifisert inngående kanal for ${hovedDokument.dokumentType.navn}.")
        }

        avsenderMottakerInfoSet.forEach {
            val partId = getPartIdFromIdentifikator(it.identifikator)
            if (partId.type.id == PartIdType.VIRKSOMHET.id) {
                val organisasjon = eregClient.hentNoekkelInformasjonOmOrganisasjon(partId.value)
                if (!organisasjon.isActive() && hovedDokument.isUtgaaende()) {
                    invalidProperties += InvalidProperty(
                        field = partId.value,
                        reason = "Organisasjon er avviklet, og kan ikke være mottaker.",
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
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        val dokument =
            dokumentUnderArbeidRepository.findById(hoveddokumentId).get() as DokumentUnderArbeidAsHoveddokument
        if (dokument.dokumentType.isInngaaende()) {
            throw DokumentValidationException("${dokument.dokumentType.navn} støtter ikke vedleggsoversikt.")
        }

        val title = "Innholdsfortegnelse"

        return FysiskDokument(
            title = title,
            content = dokumentService.changeTitleInPDF(
                documentBytes = innholdsfortegnelseService.getInnholdsfortegnelseAsPdf(
                    dokumentUnderArbeidId = hoveddokumentId,
                    fnr = behandling.sakenGjelder.partId.value,
                ),
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
        val dokument = dokumentUnderArbeidRepository.findById(dokumentId).get()

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
        val document = dokumentUnderArbeidRepository.findById(dokumentId).get()

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(
            behandlingId = document.behandlingId,
        )

        //first vedlegg

        val vedlegg = dokumentUnderArbeidCommonService.findVedleggByParentId(dokumentId)
            .map {
                if (it.erMarkertFerdig()) {
                    throw MissingTilgangException("Attempting to delete finalized document ${document.id}")
                }
                it
            }.toSet()

        deleteDocuments(
            documentSet = vedlegg,
            innloggetIdent = innloggetIdent,
            behandlingRole = behandling.getRoleInBehandling(innloggetIdent),
            behandling = behandling,
        )

        if (document.erMarkertFerdig()) {
            throw MissingTilgangException("Attempting to delete finalized document ${document.id}")
        }

        //then hoveddokument
        deleteDocuments(
            documentSet = setOf(document),
            innloggetIdent = innloggetIdent,
            behandlingRole = behandling.getRoleInBehandling(innloggetIdent),
            behandling = behandling,
        )

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                DocumentsRemovedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    idList = vedlegg.map { it.id.toString() } + document.id.toString(),
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.DOCUMENTS_REMOVED,
        )
    }

    private fun deleteDocuments(
        documentSet: Set<DokumentUnderArbeid>,
        innloggetIdent: String,
        behandlingRole: BehandlingRole,
        behandling: Behandling,
    ) {
        documentSet.forEach { document ->
            if (behandling.avsluttetAvSaksbehandler == null) {
                if (document.creatorRole != behandlingRole && !innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter()) {
                    throw MissingTilgangException("$behandlingRole har ikke anledning til å slette dokumentet eiet av ${document.creatorRole}.")
                }
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

        }

        dokumentUnderArbeidRepository.deleteAll(documentSet)

        behandling.publishEndringsloggEvent(
            saksbehandlerident = innloggetIdent,
            felt = Felt.DOKUMENT_UNDER_ARBEID_SLETTET,
            fraVerdi = documentSet.joinToString { it.id.toString() },
            tilVerdi = null,
            tidspunkt = LocalDateTime.now(),
        )
    }

    fun setAsVedlegg(
        newParentId: UUID,
        dokumentId: UUID,
        innloggetIdent: String
    ): Pair<List<DokumentUnderArbeid>, List<JournalfoertDokumentUnderArbeidAsVedlegg>> {
        if (newParentId == dokumentId) {
            throw DokumentValidationException("Kan ikke gjøre et dokument til vedlegg for seg selv.")
        }
        val parentDocument =
            dokumentUnderArbeidRepository.findById(newParentId).get() as DokumentUnderArbeidAsHoveddokument

        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(
            behandlingId = parentDocument.behandlingId,
        )

        if (parentDocument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble til et dokument som er ferdigstilt")
        }
        val currentDocument = dokumentUnderArbeidRepository.findById(dokumentId).get()

        if (parentDocument.isInngaaende()) {
            if (!((currentDocument is OpplastetDokumentUnderArbeidAsVedlegg) || (currentDocument is OpplastetDokumentUnderArbeidAsHoveddokument))) {
                throw DokumentValidationException("${parentDocument.dokumentType.navn} kan bare ha opplastet dokument som vedlegg.")
            }
        }

        val descendants = getVedlegg(hoveddokumentId = dokumentId)

        val dokumentIdSet = mutableListOf<UUID>()
        dokumentIdSet += descendants.map { it.id }
        dokumentIdSet += dokumentId

        val processedDokumentUnderArbeidOutput = dokumentIdSet.map { currentDokumentId ->
            setParentInDokumentUnderArbeidAndFindDuplicates(currentDokumentId, newParentId)
        }

        val alteredDocuments = processedDokumentUnderArbeidOutput.mapNotNull { it.first }
        val duplicateJournalfoerteDokumenterUnderArbeid: List<JournalfoertDokumentUnderArbeidAsVedlegg> =
            processedDokumentUnderArbeidOutput.mapNotNull { it.second }

        duplicateJournalfoerteDokumenterUnderArbeid.forEach {
            dokumentUnderArbeidRepository.deleteById(it.id)
        }

        return alteredDocuments to duplicateJournalfoerteDokumenterUnderArbeid
    }

    private fun setParentInDokumentUnderArbeidAndFindDuplicates(
        currentDokumentId: UUID,
        parentId: UUID,
    ): Pair<DokumentUnderArbeid?, JournalfoertDokumentUnderArbeidAsVedlegg?> {
        var dokumentUnderArbeid: DokumentUnderArbeid =
            dokumentUnderArbeidRepository.findById(currentDokumentId).get()

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
    ): DokumentUnderArbeidAsHoveddokument {
        val dokument = dokumentUnderArbeidRepository.findById(dokumentId).get()

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(
            behandlingId = dokument.behandlingId,
        )
        //TODO: Skal det lages endringslogg på dette??

        if (dokument is DokumentUnderArbeidAsHoveddokument) {
            throw DokumentValidationException("Dokumentet er allerede hoveddokument.")
        }

        if (dokument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke frikoble et dokument som er ferdigstilt")
        }

        dokument as DokumentUnderArbeidAsVedlegg

        val parentDocument =
            dokumentUnderArbeidRepository.findById(dokument.parentId).get() as DokumentUnderArbeidAsHoveddokument

        val savedDocument = when (dokument) {
            is OpplastetDokumentUnderArbeidAsVedlegg -> {
                //delete first so we can reuse the id
                opplastetDokumentUnderArbeidAsVedleggRepository.delete(dokument)

                opplastetDokumentUnderArbeidAsHoveddokumentRepository.save(
                    dokument.asHoveddokument(dokumentType = parentDocument.dokumentType)
                )
            }

            is SmartdokumentUnderArbeidAsVedlegg -> {
                //delete first so we can reuse the id
                smartDokumentUnderArbeidAsVedleggRepository.delete(dokument)

                smartDokumentUnderArbeidAsHoveddokumentRepository.save(
                    dokument.asHoveddokument(dokumentType = parentDocument.dokumentType)
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

    fun getDokumenterUnderArbeidViewList(behandlingId: UUID): List<DokumentView> {
        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        return getDokumentViewList(
            dokumentUnderArbeidList = dokumentUnderArbeidRepository.findByBehandlingIdAndFerdigstiltIsNull(behandlingId),
            behandling = behandling,
        )
    }

    fun getDokumentUnderArbeidView(dokumentUnderArbeidId: UUID, behandlingId: UUID): DokumentView {
        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        return getDokumentViewList(
            dokumentUnderArbeidList = listOf(dokumentUnderArbeidRepository.findById(dokumentUnderArbeidId).get()),
            behandling = behandling,
        ).first()
    }

    @Suppress("UNCHECKED_CAST")
    fun getDokumentViewList(
        dokumentUnderArbeidList: List<DokumentUnderArbeid>,
        behandling: Behandling,
    ): List<DokumentView> {
        val (dokumenterUnderArbeid, journalfoerteDokumenterUnderArbeid) = dokumentUnderArbeidList.partition {
            it !is JournalfoertDokumentUnderArbeidAsVedlegg
        } as Pair<List<DokumentUnderArbeid>, List<JournalfoertDokumentUnderArbeidAsVedlegg>>

        val journalpostList =
            if (journalfoerteDokumenterUnderArbeid.isNotEmpty()) {
                safFacade.getJournalposter(
                    journalpostIdSet = journalfoerteDokumenterUnderArbeid.map { it.journalpostId }.toSet(),
                    fnr = behandling.sakenGjelder.partId.value,
                    saksbehandlerContext = true,
                )
            } else emptyList()

        return dokumenterUnderArbeid.sortedByDescending { it.created }
            .map {
                val smartEditorDocument = if (it is DokumentUnderArbeidAsSmartdokument) {
                    kabalSmartEditorApiClient.getDocument(documentId = it.smartEditorId)
                } else null
                dokumentMapper.mapToDokumentView(
                    dokumentUnderArbeid = it,
                    journalpost = null,
                    smartEditorDocument = smartEditorDocument,
                    behandling = behandling
                )
            }
            .plus(
                getDokumentViewListForJournalfoertDokumentUnderArbeidAsVedleggList(
                    dokumentUnderArbeidList = journalfoerteDokumenterUnderArbeid,
                    behandling = behandling,
                    journalpostList = journalpostList,
                )
            )
    }

    fun opprettDokumentEnhet(hovedDokumentId: UUID): DokumentUnderArbeidAsHoveddokument {
        logger.debug("opprettDokumentEnhet hoveddokument with id {}", hovedDokumentId)
        val hovedDokument =
            dokumentUnderArbeidRepository.findById(hovedDokumentId).get() as DokumentUnderArbeidAsHoveddokument
        logger.debug("got hoveddokument with id {}, dokmentEnhetId {}", hovedDokumentId, hovedDokument.dokumentEnhetId)
        val vedlegg = dokumentUnderArbeidCommonService.findVedleggByParentId(hovedDokument.id)
        logger.debug("got vedlegg for hoveddokument id {}, size: {}", hovedDokumentId, vedlegg.size)
        //Denne er alltid sann
        if (hovedDokument.dokumentEnhetId == null) {
            logger.debug("hoveddokument.dokumentEnhetId == null, id {}", hovedDokumentId)
            //Vi vet at smartEditor-dokumentene har en oppdatert snapshot i mellomlageret fordi det ble fikset i finnOgMarkerFerdigHovedDokument
            val behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(hovedDokument.behandlingId)
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
            dokumentUnderArbeidRepository.findById(hovedDokumentId).get() as DokumentUnderArbeidAsHoveddokument
        val vedlegg = dokumentUnderArbeidCommonService.findVedleggByParentId(hovedDokument.id)
        val behandling: Behandling =
            behandlingService.getBehandlingForReadWithoutCheckForAccess(hovedDokument.behandlingId)

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

        val journalpostSet = safFacade.getJournalposter(
            journalpostIdSet = journalpostIdSet,
            fnr = behandling.sakenGjelder.partId.value,
            saksbehandlerContext = false,
        )

        journalpostIdSet.forEach { documentInfo ->
            val journalpost = journalpostSet.find { it.journalpostId == documentInfo }
            val saksbehandlerIdent = systembrukerIdent
            val saksdokumenter = journalpost.mapToSaksdokumenter()

            if (behandling.avsluttetAvSaksbehandler == null) {
                saksdokumenter.forEach { saksdokument ->
                    behandling.addSaksdokument(saksdokument, saksbehandlerIdent)
                        ?.also { applicationEventPublisher.publishEvent(it) }
                }
            }
        }

        dokumentEnhetFullfoerOutput.sourceReferenceWithJoarkReferencesList.filter { it.sourceReference != null }
            .forEach { sourceReferenceWithJoarkReferences ->
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

        logger.debug(
            "about to call ferdigstillHvisIkkeAlleredeFerdigstilt(now) for vedlegg of dokument with id {}",
            hovedDokumentId
        )
        vedlegg.forEach {
            it.ferdigstillHvisIkkeAlleredeFerdigstilt(now)
        }
        logger.debug("about to return hoveddokument for hoveddokumentId {}", hovedDokumentId)
        return hovedDokument
    }

    fun getSmartEditorId(dokumentId: UUID, readOnly: Boolean): UUID {
        val dokumentUnderArbeid = dokumentUnderArbeidRepository.findById(dokumentId).get()

        if (dokumentUnderArbeid !is DokumentUnderArbeidAsSmartdokument) {
            throw RuntimeException("dokument is not smartdokument")
        }

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(dokumentUnderArbeid.behandlingId)

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

    private fun Journalpost?.mapToSaksdokumenter(): List<Saksdokument> {
        return this?.dokumenter?.map {
            Saksdokument(
                journalpostId = this.journalpostId,
                dokumentInfoId = it.dokumentInfoId
            )
        } ?: emptyList()
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
}


