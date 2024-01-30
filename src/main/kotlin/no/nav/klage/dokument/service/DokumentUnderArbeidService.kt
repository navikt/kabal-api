package no.nav.klage.dokument.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import jakarta.transaction.Transactional
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.*
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.dokument.clients.kabaljsontopdf.KabalJsonToPdfClient
import no.nav.klage.dokument.clients.kabalsmarteditorapi.KabalSmartEditorApiClient
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
    private val meterRegistry: MeterRegistry,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
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
        datoMottatt: LocalDate?,
    ): DokumentView {
        //Sjekker lesetilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        if (dokumentType != DokumentType.KJENNELSE_FRA_TRYGDERETTEN || !innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter()) {
            if (behandling.avsluttetAvSaksbehandler == null) {
                validateCanCreateDocuments(
                    behandlingRole = behandlingRole,
                    parentDocument = if (parentId != null) dokumentUnderArbeidRepository.findById(parentId)
                        .get() else null
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
                    datoMottatt = if (dokumentType == DokumentType.KJENNELSE_FRA_TRYGDERETTEN) datoMottatt else null,
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
        )

        val dokumentView = dokumentMapper.mapToDokumentView(
            dokumentUnderArbeid = document,
            journalpost = null,
            smartEditorDocument = null,
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
                parentId = optionalParentInput.dokumentId,
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
                            dokumentTypeId = it.dokumentType?.id,
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
                parentDocument = if (parentId != null) dokumentUnderArbeidRepository.findById(parentId).get() else null
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
        )

        val smartEditorDocument = kabalSmartEditorApiClient.getDocument(documentId = document.smartEditorId)

        val dokumentView = dokumentMapper.mapToDokumentView(
            dokumentUnderArbeid = document,
            journalpost = null,
            smartEditorDocument = smartEditorDocument,
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

        val parentDocument = dokumentUnderArbeidRepository.getReferenceById(journalfoerteDokumenterInput.parentId)

        if (parentDocument.dokumentType == DokumentType.KJENNELSE_FRA_TRYGDERETTEN) {
            throw DokumentValidationException("Kan ikke sette journalførte dokumenter som vedlegg til kjennelse fra Trygderetten.")
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
        val parentDocument = dokumentUnderArbeidRepository.findById(parentId).get()

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
                dokumentType = null,
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

    fun getDokumentUnderArbeid(dokumentId: UUID) = dokumentUnderArbeidRepository.findById(dokumentId).get()

    fun updateDokumentType(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        dokumentType: DokumentType,
        innloggetIdent: String
    ): DokumentUnderArbeid {
        val dokumentUnderArbeid = dokumentUnderArbeidRepository.findById(dokumentId).get()

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

        if (dokumentType == DokumentType.KJENNELSE_FRA_TRYGDERETTEN) {
            val children = dokumentUnderArbeidCommonService.findVedleggByParentId(dokumentId)
            if (children.any { it !is OpplastetDokumentUnderArbeidAsVedlegg }) {
                throw DokumentValidationException("Kjennelse fra Trygderetten kan kun ha opplastede vedlegg. Fjern ugyldige vedlegg og prøv på nytt.")
            }
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
                            dokumentTypeId = dokumentUnderArbeid.dokumentType?.id,
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

    fun updateDatoMottatt(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        datoMottatt: LocalDate,
        innloggetIdent: String
    ): DokumentUnderArbeid {
        val dokumentUnderArbeid = dokumentUnderArbeidRepository.findById(dokumentId).get()

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke sette dato mottatt på et dokument som er ferdigstilt")
        }

        if (datoMottatt.isAfter(LocalDate.now())) {
            throw DokumentValidationException("Kan ikke sette dato mottatt i fremtiden")
        }

        if (dokumentUnderArbeid.dokumentType != DokumentType.KJENNELSE_FRA_TRYGDERETTEN) {
            throw DokumentValidationException("Kan bare sette dato mottatt på inngående dokument.")
        }

        dokumentUnderArbeid as OpplastetDokumentUnderArbeidAsHoveddokument

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
                            parentId = if (dokumentUnderArbeid is DokumentUnderArbeidAsVedlegg) dokumentUnderArbeid.parentId.toString() else null,
                            dokumentTypeId = dokumentUnderArbeid.dokumentType?.id,
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
                            dokumentTypeId = dokumentUnderArbeid.dokumentType?.id,
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
        brevmottakerInfoSet: Set<BrevmottakerInfo>?,
    ): DokumentUnderArbeid {
        val hovedDokument = dokumentUnderArbeidRepository.findById(dokumentId).get()
        val processedBrevmottakerInfoSet = if (hovedDokument.dokumentType == DokumentType.KJENNELSE_FRA_TRYGDERETTEN) {
            //Hardkoder Trygderetten
            setOf(
                BrevmottakerInfo(
                    id = "974761084", localPrint = false
                )
            )
        } else brevmottakerInfoSet

        if (hovedDokument !is DokumentUnderArbeidAsHoveddokument) {
            throw RuntimeException("document is not hoveddokument")
        }

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(hovedDokument.behandlingId)

        validateHoveddokumentBeforeFerdig(
            brevmottakerInfoSet = processedBrevmottakerInfoSet,
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

        processedBrevmottakerInfoSet?.forEach {
            hovedDokument.brevmottakerInfoSet.add(
                it.toDokumentUnderArbeidBrevmottakerInfo(
                    dokumentType = hovedDokument.dokumentType!!,
                    behandling = behandling
                )
            )
        }

        vedlegg.forEach {
            it.markerFerdigHvisIkkeAlleredeMarkertFerdig(
                tidspunkt = now,
                saksbehandlerIdent = innloggetIdent
            )
        }

        if (vedlegg.isNotEmpty() && hovedDokument.dokumentType != DokumentType.KJENNELSE_FRA_TRYGDERETTEN) {
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
            tilVerdi = hovedDokument.brevmottakerInfoSet.joinToString { it.identifikator },
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
                            dokumentTypeId = it.dokumentType?.id,
                            tittel = it.name,
                            isMarkertAvsluttet = it.erMarkertFerdig(),
                        )
                    } + DocumentsChangedEvent.DocumentChanged(
                        id = hovedDokument.id.toString(),
                        parentId = null,
                        dokumentTypeId = hovedDokument.dokumentType?.id,
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

    private fun BrevmottakerInfo.toDokumentUnderArbeidBrevmottakerInfo(
        dokumentType: DokumentType,
        behandling: Behandling,
    ): DokumentUnderArbeidBrevmottakerInfo {
        return if (dokumentType == DokumentType.NOTAT) {
            DokumentUnderArbeidBrevmottakerInfo(
                identifikator = behandling.sakenGjelder.partId.value,
                localPrint = false,
            )
        } else {
            DokumentUnderArbeidBrevmottakerInfo(
                identifikator = id,
                localPrint = localPrint,
            )
        }
    }

    private fun validateHoveddokumentBeforeFerdig(
        brevmottakerInfoSet: Set<BrevmottakerInfo>?,
        hovedDokument: DokumentUnderArbeid,
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

        if (hovedDokument.dokumentType != DokumentType.NOTAT && brevmottakerInfoSet.isNullOrEmpty()) {
            throw DokumentValidationException("Brevmottakere må være satt")
        }

        brevmottakerInfoSet?.forEach {
            val partId = getPartIdFromIdentifikator(it.id)
            if (partId.type.id == PartIdType.VIRKSOMHET.id) {
                val organisasjon = eregClient.hentOrganisasjon(partId.value)
                if (!organisasjon.isActive()) {
                    invalidProperties += InvalidProperty(
                        field = partId.value,
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
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        val dokument = dokumentUnderArbeidRepository.getReferenceById(hoveddokumentId)
        if (dokument.dokumentType == DokumentType.KJENNELSE_FRA_TRYGDERETTEN) {
            throw DokumentValidationException("Kjennelse fra Trygderetten støtter ikke vedleggsoversikt.")
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
        parentId: UUID,
        dokumentId: UUID,
        innloggetIdent: String
    ): Pair<List<DokumentUnderArbeid>, List<JournalfoertDokumentUnderArbeidAsVedlegg>> {
        if (parentId == dokumentId) {
            throw DokumentValidationException("Kan ikke gjøre et dokument til vedlegg for seg selv.")
        }
        val parentDocument = dokumentUnderArbeidRepository.findById(parentId).get()

        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(
            behandlingId = parentDocument.behandlingId,
        )

        if (parentDocument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble til et dokument som er ferdigstilt")
        }
        val currentDocument = dokumentUnderArbeidRepository.findById(dokumentId).get()

        if (currentDocument.dokumentType == DokumentType.KJENNELSE_FRA_TRYGDERETTEN) {
            if (parentDocument !is OpplastetDokumentUnderArbeidAsHoveddokument) {
                throw DokumentValidationException("Dette dokumentet kan kun være vedlegg til opplastet dokument.")
            }
        }

        if (parentDocument.dokumentType == DokumentType.KJENNELSE_FRA_TRYGDERETTEN) {
            if (!((currentDocument is OpplastetDokumentUnderArbeidAsVedlegg) || (currentDocument is OpplastetDokumentUnderArbeidAsHoveddokument))) {
                throw DokumentValidationException("Kjennelse fra Trygderetten kan bare ha opplastet dokument som vedlegg.")
            }
        }

        val descendants = getVedlegg(hoveddokumentId = dokumentId)

        val dokumentIdSet = mutableSetOf(dokumentId)
        dokumentIdSet += descendants.map { it.id }

        val processedDokumentUnderArbeidOutput = dokumentIdSet.map { currentDokumentId ->
            setParentInDokumentUnderArbeidAndFindDuplicates(currentDokumentId, parentId)
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
    ): DokumentUnderArbeid {
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

        val savedDocument = when (dokument) {
            is OpplastetDokumentUnderArbeidAsVedlegg -> {
                //delete first so we can reuse the id
                opplastetDokumentUnderArbeidAsVedleggRepository.delete(dokument)

                opplastetDokumentUnderArbeidAsHoveddokumentRepository.save(
                    dokument.asHoveddokument()
                )
            }

            is SmartdokumentUnderArbeidAsVedlegg -> {
                //delete first so we can reuse the id
                smartDokumentUnderArbeidAsVedleggRepository.delete(dokument)

                smartDokumentUnderArbeidAsHoveddokumentRepository.save(
                    dokument.asHoveddokument()
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
                    smartEditorDocument = smartEditorDocument
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


