package no.nav.klage.dokument.service

import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.http.HttpServletRequest
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.*
import no.nav.klage.dokument.domain.PDFDocument
import no.nav.klage.dokument.domain.SmartDocumentAccessDocumentEvent
import no.nav.klage.dokument.domain.dokumenterunderarbeid.*
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid.Companion.MAX_NAME_LENGTH
import no.nav.klage.dokument.exceptions.*
import no.nav.klage.dokument.gateway.DefaultKabalSmartEditorApiGateway
import no.nav.klage.dokument.repositories.*
import no.nav.klage.dokument.util.DuaAccessPolicy
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.api.view.DokumentReferanse
import no.nav.klage.oppgave.api.view.DokumentUnderArbeidMetadata
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.clients.saf.graphql.Journalpost
import no.nav.klage.oppgave.clients.saf.graphql.Journalstatus
import no.nav.klage.oppgave.config.getHistogram
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.embedded.Prosessfullmektig
import no.nav.klage.oppgave.domain.behandling.setters.BehandlingSetters.addSaksdokument
import no.nav.klage.oppgave.domain.behandling.subentities.ForlengetBehandlingstidDraft
import no.nav.klage.oppgave.domain.behandling.subentities.Saksdokument
import no.nav.klage.oppgave.domain.events.DokumentFerdigstiltAvSaksbehandler
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.service.*
import no.nav.klage.oppgave.util.*
import org.apache.commons.fileupload2.jakarta.JakartaServletFileUpload
import org.hibernate.Hibernate
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.BufferedReader
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.time.measureTime


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
    private val mellomlagerService: MellomlagerService,
    private val smartEditorApiGateway: DefaultKabalSmartEditorApiGateway,
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
    private val partSearchService: PartSearchService,
    meterRegistry: MeterRegistry,
    private val kodeverkService: KodeverkService,
    private val dokDistKanalService: DokDistKanalService,
    private val kabalJsonToPdfService: KabalJsonToPdfService,
    private val tokenUtil: TokenUtil,
    @Value("\${INNSYNSBEGJAERING_TEMPLATE_ID}") private val innsynsbegjaeringTemplateId: String,
    @Value("\${ORGANISASJONSNUMMER_TRYGDERETTEN}") private val organisasjonsnummerTrygderetten: String,
    @Value("\${spring.profiles.active:}") private val activeSpringProfile: String,
    private val documentPolicyService: DocumentPolicyService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val jacksonObjectMapper = jacksonObjectMapper()
        private val DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd. MMM yyyy", Locale.of("nb", "NO")).withZone(ZoneId.of("Europe/Oslo"))
    }

    private val metricForSmartDocumentVersions = meterRegistry.getHistogram(
        name = "smartDocument.versions",
        baseUnit = "versions",
    )

    fun createOpplastetDokumentUnderArbeid(
        behandlingId: UUID,
        dokumentTypeId: String,
        parentId: UUID?,
        file: File,
        filename: String?,
        utfoerendeIdent: String,
        systemContext: Boolean,
    ): DokumentView {
        val dokumentType = DokumentType.of(dokumentTypeId)

        val title = filename ?: (dokumentType.defaultFilnavn.also { logger.warn("Filnavn ikke angitt i fil-request") })

        //Sjekker lesetilgang på behandlingsnivå:
        val behandling = if (systemContext) {
            behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(behandlingId)
        } else {
            behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        }
        val behandlingRole = behandling.getRoleInBehandling(utfoerendeIdent)

        val duration = measureTime {
            documentPolicyService.validateDokumentUnderArbeidAction(
                behandling = behandling,
                dokumentType = DuaAccessPolicy.DokumentType.UPLOADED,
                parentDokumentType = documentPolicyService.getParentDokumentType(parentDuaId = parentId),
                documentRole = behandlingRole,
                action = DuaAccessPolicy.Action.CREATE,
                duaMarkertFerdig = false,
                isSystemContext = systemContext,
            )
        }
        logger.debug(
            "Validated createOpplastetDokumentUnderArbeid action. Duration: {} ms",
            duration.inWholeMilliseconds
        )

        //File gets deleted when uploading, so keep this for later.
        val fileSize = file.length()
        val mellomlagerId = mellomlagerService.uploadFile(file = file, systemContext = systemContext)

        val now = LocalDateTime.now()

        val document = if (parentId == null) {
            opplastetDokumentUnderArbeidAsHoveddokumentRepository.save(
                OpplastetDokumentUnderArbeidAsHoveddokument(
                    mellomlagerId = mellomlagerId,
                    mellomlagretDate = now,
                    size = fileSize,
                    name = title,
                    dokumentType = dokumentType,
                    behandlingId = behandlingId,
                    creatorIdent = utfoerendeIdent,
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
                    size = file.length(),
                    name = title,
                    behandlingId = behandlingId,
                    creatorIdent = utfoerendeIdent,
                    creatorRole = behandlingRole,
                    parentId = parentId,
                    created = now,
                    modified = now,
                )
            )
        }

        val dokumentView = dokumentMapper.mapToDokumentView(
            dokumentUnderArbeid = document,
            journalpost = null,
            smartEditorDocument = null,
            behandling = behandling,
        )

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                DocumentsAddedEvent(
                    actor = Employee(
                        navIdent = utfoerendeIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeIdent),
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

    fun createOpplastetDokumentUnderArbeid(
        behandlingId: UUID,
        uploadRequest: HttpServletRequest,
        innloggetIdent: String,
    ): DokumentView {
        var dokumentTypeId = ""
        var parentId: UUID? = null
        var filename: String? = null

        var start = System.currentTimeMillis()
        val filePath = Files.createTempFile(null, null)
        logger.debug("Created temp file in {} ms", System.currentTimeMillis() - start)

        start = System.currentTimeMillis()
        val contentLength = uploadRequest.getHeader("Content-Length")?.toInt() ?: 0
        logger.debug(
            "Checked Content-Length header in {} ms. It was {}",
            (System.currentTimeMillis() - start),
            contentLength
        )

        //500 MiB
        if (contentLength > 524288000) {
            throw AttachmentTooLargeException()
        }

        val upload = JakartaServletFileUpload()
        val parts = upload.getItemIterator(uploadRequest)
        logger.debug("parts: {}", parts)
        parts.forEachRemaining { item ->
            logger.debug("item: {}", item)
            val fieldName = item.fieldName
            start = System.currentTimeMillis()
            val inputStream = item.inputStream
            logger.debug("Got input stream in {} ms", System.currentTimeMillis() - start)
            if (!item.isFormField) {
                filename = item.name
                try {
                    start = System.currentTimeMillis()
                    Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING)
                    logger.debug("Copied file to temp file in {} ms", System.currentTimeMillis() - start)
                } catch (e: Exception) {
                    throw RuntimeException("Failed to save file", e)
                } finally {
                    inputStream.close()
                }
            } else {
                try {
                    start = System.currentTimeMillis()
                    val content = inputStream.bufferedReader().use(BufferedReader::readText)
                    if (fieldName == "dokumentTypeId") {
                        dokumentTypeId = content
                    } else if (fieldName == "parentId" && content.isNotBlank()) {
                        parentId = UUID.fromString(content)
                    }
                    logger.debug("Read content in {} ms", System.currentTimeMillis() - start)
                } catch (e: Exception) {
                    throw RuntimeException("Failed to read content", e)
                } finally {
                    inputStream.close()
                }
            }
        }

        return createOpplastetDokumentUnderArbeid(
            behandlingId = behandlingId,
            dokumentTypeId = dokumentTypeId,
            parentId = parentId,
            file = filePath.toFile(),
            filename = filename,
            utfoerendeIdent = innloggetIdent,
            systemContext = false,
        )
    }

    fun kobleEllerFrikobleVedlegg(
        behandlingId: UUID,
        persistentDokumentId: UUID,
        optionalParentDocumentId: UUID?,
    ): DokumentViewWithList {
        if (optionalParentDocumentId == null) {
            throw DokumentValidationException("Per i dag støttes ikke å gjøre om vedlegg til hovedokument.")
        }

        val dua = getDokumentUnderArbeid(persistentDokumentId)

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId = behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetSaksbehandlerService.getInnloggetIdent())

        var duration = measureTime {
            documentPolicyService.validateDokumentUnderArbeidAction(
                behandling = behandling,
                dokumentType = documentPolicyService.getDokumentType(duaId = persistentDokumentId),
                parentDokumentType = documentPolicyService.getParentDokumentType(parentDuaId = optionalParentDocumentId),
                documentRole = dua.creatorRole,
                action = DuaAccessPolicy.Action.REMOVE,
                duaMarkertFerdig = dua.erMarkertFerdig(),
            )
        }
        logger.debug(
            "Validated kobleEllerFrikobleVedlegg REMOVE action. Duration: {} ms",
            duration.inWholeMilliseconds
        )

        duration = measureTime {
            documentPolicyService.validateDokumentUnderArbeidAction(
                behandling = behandling,
                dokumentType = documentPolicyService.getDokumentType(duaId = persistentDokumentId),
                parentDokumentType = documentPolicyService.getParentDokumentType(parentDuaId = optionalParentDocumentId),
                documentRole = behandlingRole,
                action = DuaAccessPolicy.Action.CREATE,
                duaMarkertFerdig = dua.erMarkertFerdig(),
            )
        }
        logger.debug(
            "Validated kobleEllerFrikobleVedlegg CREATE action. Duration: {} ms",
            duration.inWholeMilliseconds
        )

        val (dokumentUnderArbeidList, duplicateJournalfoerteDokumenter) = setAsVedlegg(
            newParentId = optionalParentDocumentId,
            dokumentId = persistentDokumentId,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )

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
            data = jacksonObjectMapper.writeValueAsString(
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
            val parentDocument = getDokumentUnderArbeid(dokumentUnderArbeid.parentId)
            parentDocument as DokumentUnderArbeidAsHoveddokument
            parentDocument.dokumentType.id
        }
    }

    fun addJournalfoerteDokumenterAsVedlegg(
        behandlingId: UUID,
        journalfoerteDokumenterInput: JournalfoerteDokumenterInput,
        innloggetIdent: String
    ): JournalfoerteDokumenterResponse {
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        val duration = measureTime {
            documentPolicyService.validateDokumentUnderArbeidAction(
                behandling = behandling,
                dokumentType = DuaAccessPolicy.DokumentType.JOURNALFOERT,
                parentDokumentType = documentPolicyService.getParentDokumentType(parentDuaId = journalfoerteDokumenterInput.parentId),
                documentRole = behandlingRole,
                action = DuaAccessPolicy.Action.CREATE,
                duaMarkertFerdig = false,
            )
        }
        logger.debug(
            "Validated addJournalfoerteDokumenterAsVedlegg CREATE action. Duration: {} ms",
            duration.inWholeMilliseconds
        )

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
                data = jacksonObjectMapper.writeValueAsString(
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
        return dokumentUnderArbeidList.sortedBy { it.sortKey }
            .map { journalfoertVedlegg ->
                dokumentMapper.mapToDokumentView(
                    dokumentUnderArbeid = journalfoertVedlegg,
                    journalpost = journalpostList.find { it.journalpostId == journalfoertVedlegg.journalpostId }!!,
                    smartEditorDocument = null,
                    behandling = behandling,
                )
            }
    }

    private fun createJournalfoerteDokumenter(
        parentId: UUID,
        journalfoerteDokumenter: Set<JournalfoertDokumentReference>,
        behandling: Behandling,
        innloggetIdent: String,
        journalpostListForUser: List<Journalpost>,
    ): Pair<List<JournalfoertDokumentUnderArbeidAsVedlegg>, List<JournalfoertDokumentReference>> {
        val parentDocument =
            getDokumentUnderArbeid(parentId) as DokumentUnderArbeidAsHoveddokument

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        if (behandling.ferdigstilling == null) {
            val isCurrentROL = behandling.rolIdent == innloggetIdent

            val templateId =
                if (parentDocument is DokumentUnderArbeidAsSmartdokument) parentDocument.smartEditorTemplateId else null

            if (templateId != innsynsbegjaeringTemplateId) {
                behandlingService.connectDocumentsToBehandling(
                    behandlingId = behandling.id,
                    journalfoertDokumentReferenceSet = journalfoerteDokumenter,
                    saksbehandlerIdent = innloggetIdent,
                    systemUserContext = false,
                    ignoreCheckSkrivetilgang = isCurrentROL
                )
            }
        }

        val alreadyAddedDocuments =
            journalfoertDokumentUnderArbeidRepository.findByParentId(parentId)

        val alreadAddedDocumentsMapped = alreadyAddedDocuments.map {
            JournalfoertDokumentReference(
                journalpostId = it.journalpostId,
                dokumentInfoId = it.dokumentInfoId,
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
                ),
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

    fun getDokumentUnderArbeid(dokumentId: UUID): DokumentUnderArbeid =
        dokumentUnderArbeidRepository.findById(dokumentId).orElseThrow {
            throw DocumentDoesNotExistException("Dokumentet med id $dokumentId finnes ikke.")
        }

    fun updateDokumentType(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        newDokumentType: DokumentType,
        innloggetIdent: String
    ): DokumentUnderArbeidAsHoveddokument {
        val dokumentUnderArbeid = getDokumentUnderArbeid(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        if (dokumentUnderArbeid !is DokumentUnderArbeidAsHoveddokument) {
            throw DokumentValidationException("Kan ikke endre dokumenttype på vedlegg")
        }

        val duration = measureTime {
            documentPolicyService.validateDokumentUnderArbeidAction(
                behandling = behandling,
                dokumentType = documentPolicyService.getDokumentType(duaId = dokumentId),
                parentDokumentType = documentPolicyService.getParentDokumentType(parentDuaId = null),
                documentRole = dokumentUnderArbeid.creatorRole,
                action = DuaAccessPolicy.Action.CHANGE_TYPE,
                duaMarkertFerdig = dokumentUnderArbeid.erMarkertFerdig(),
            )
        }
        logger.debug(
            "Validated updateDokumentType action. Duration: {} ms",
            duration.inWholeMilliseconds
        )

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
        dokumentUnderArbeid.dokumentType = newDokumentType

        dokumentUnderArbeid.modified = LocalDateTime.now()

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
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
            getDokumentUnderArbeid(dokumentId) as OpplastetDokumentUnderArbeidAsHoveddokument

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

        dokumentUnderArbeid.datoMottatt = datoMottatt

        dokumentUnderArbeid.modified = LocalDateTime.now()

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
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
        val dokumentUnderArbeid = getDokumentUnderArbeid(dokumentId) as OpplastetDokumentUnderArbeidAsHoveddokument

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke sette inngående kanal på et dokument som er ferdigstilt")
        }

        if (dokumentUnderArbeid.dokumentType != DokumentType.ANNEN_INNGAAENDE_POST) {
            throw DokumentValidationException("Kan bare sette inngående kanal på ${DokumentType.ANNEN_INNGAAENDE_POST.navn}.")
        }

        dokumentUnderArbeid.inngaaendeKanal = inngaaendeKanal

        dokumentUnderArbeid.modified = LocalDateTime.now()

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
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
            identifikator = avsenderInput.identifikator,
            systemUserContext = true
        )

        val dokumentUnderArbeid = getDokumentUnderArbeid(dokumentId)

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

        val technicalPartId = behandling.getTechnicalIdFromPart(identifikator = avsenderInput.identifikator)

        dokumentUnderArbeid.brevmottakere.clear()
        dokumentUnderArbeid.brevmottakere.add(
            Brevmottaker(
                technicalPartId = technicalPartId,
                identifikator = avsenderInput.identifikator,
                localPrint = false,
                forceCentralPrint = false,
                address = null,
                navn = null,
            )
        )

        dokumentUnderArbeid.modified = LocalDateTime.now()

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
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

    //TODO: Undersøk om vi gjør dette kallet unødvendig når vi sender ut svarbrev fra mottak.
    fun updateMottakere(
        behandlingId: UUID,
        dokumentId: UUID,
        mottakerInput: MottakerInput,
        utfoerendeIdent: String,
        systemContext: Boolean,
    ): DokumentUnderArbeidAsHoveddokument {
        val dokumentUnderArbeid = getDokumentUnderArbeid(dokumentId)

        validateMottakerList(
            mottakerInput = mottakerInput,
            systemContext = systemContext
        )

        if (dokumentUnderArbeid.isVedlegg()) {
            throw DokumentValidationException("Kan ikke sette mottakere på vedlegg")
        }

        dokumentUnderArbeid as DokumentUnderArbeidAsHoveddokument

        val behandling = if (systemContext) {
            behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(behandlingId)
        } else {
            behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        }

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke sette mottakere på et dokument som er ferdigstilt")
        }

        if (!dokumentUnderArbeid.isUtgaaende()) {
            throw DokumentValidationException("Kan bare sette mottakere på utgående dokument")
        }

        val existingMottakere = dokumentUnderArbeid.brevmottakere
        val (mottakereToUpdate, mottakereToAdd) = mottakerInput.mottakerList.partition { inputMottaker ->
            inputMottaker.id in (existingMottakere.map { it.technicalPartId })
        }

        val mottakereToDelete = existingMottakere.filter { existingMottaker ->
            existingMottaker.technicalPartId !in (mottakerInput.mottakerList.map { it.id })
        }

        mottakerInput.mottakerList.forEach { inputMottaker ->
            val (markLocalPrint, forceCentralPrint) = getPreferredHandling(
                identifikator = inputMottaker.identifikator,
                handling = inputMottaker.handling,
                isAddressOverridden = inputMottaker.overriddenAddress != null,
                sakenGjelderFnr = behandling.sakenGjelder.partId.value,
                tema = behandling.ytelse.toTema(),
                systemContext = systemContext,
            )

            val technicalPartId =
                inputMottaker.id ?: behandling.getTechnicalIdFromPart(identifikator = inputMottaker.identifikator)

            val getAddressFromFullmektig = inputMottaker.identifikator == null
            if (getAddressFromFullmektig && behandling.prosessfullmektig?.id != technicalPartId) {
                throw DokumentValidationException("Kun fullmektig kan brukes som mottaker uten identifikator.")
            }

            when (inputMottaker) {
                in mottakereToAdd -> {
                    dokumentUnderArbeid.brevmottakere.add(
                        Brevmottaker(
                            technicalPartId = technicalPartId,
                            identifikator = inputMottaker.identifikator,
                            localPrint = markLocalPrint,
                            forceCentralPrint = forceCentralPrint,
                            address = getDokumentUnderArbeidAdresse(
                                overrideAddress = inputMottaker.overriddenAddress,
                                getAddressFromFullmektig = getAddressFromFullmektig,
                                fullmektig = behandling.prosessfullmektig,
                            ),
                            navn = inputMottaker.navn,
                        )
                    )
                }

                in mottakereToUpdate -> {
                    val existingMottaker =
                        dokumentUnderArbeid.brevmottakere.first { it.technicalPartId == technicalPartId }
                    existingMottaker.localPrint = markLocalPrint
                    existingMottaker.forceCentralPrint = forceCentralPrint
                    existingMottaker.address = getDokumentUnderArbeidAdresse(
                        overrideAddress = inputMottaker.overriddenAddress,
                        getAddressFromFullmektig = getAddressFromFullmektig,
                        fullmektig = behandling.prosessfullmektig,
                    )
                }

                else -> {
                    throw RuntimeException("Feil ved setting av mottaker med id ${inputMottaker.id}. Undersøk det tekniske.")
                }
            }
        }

        dokumentUnderArbeid.brevmottakere.removeIf { existingMottaker ->
            mottakereToDelete.any { it.technicalPartId == existingMottaker.technicalPartId }
        }

        dokumentUnderArbeid.modified = LocalDateTime.now()

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                DocumentsChangedEvent(
                    actor = Employee(
                        navIdent = utfoerendeIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeIdent),
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

    fun validateMottakerList(
        mottakerInput: MottakerInput,
        systemContext: Boolean,
    ) {
        mottakerInput.mottakerList.forEach { mottaker ->
            if (mottaker.identifikator == null && mottaker.id == null) {
                throw DokumentValidationException("Mottaker må ha enten identifikator eller id.")
            }

            if (mottaker.identifikator != null) {
                val part = partSearchService.searchPart(
                    identifikator = mottaker.identifikator,
                    systemUserContext = systemContext
                )

                when (part.type) {
                    BehandlingDetaljerView.IdType.FNR -> if (part.statusList.any { it.status == BehandlingDetaljerView.PartStatus.Status.DEAD }) {
                        throw DokumentValidationException("Mottaker ${part.name} er død, velg en annen mottaker.")
                    }

                    BehandlingDetaljerView.IdType.ORGNR -> if (part.statusList.any { it.status == BehandlingDetaljerView.PartStatus.Status.DELETED }) {
                        throw DokumentValidationException("Mottaker ${part.name} er avviklet, velg en annen mottaker.")
                    }
                }
            }

            if (mottaker.overriddenAddress != null) {
                val landkoder = kodeverkService.getLandkoder()
                if (landkoder.find { it.landkode == mottaker.overriddenAddress.landkode } == null) {
                    throw DokumentValidationException("Ugyldig landkode: ${mottaker.overriddenAddress.landkode}")
                }

                mottaker.overriddenAddress.validateAddress()
            }
        }
    }

    fun getPreferredHandling(
        identifikator: String?,
        handling: HandlingEnum,
        isAddressOverridden: Boolean,
        sakenGjelderFnr: String,
        tema: Tema,
        systemContext: Boolean
    ) = when (handling) {
        HandlingEnum.AUTO -> {
            if (identifikator == null) {
                false to false
            } else {
                val partIdType = getPartIdFromIdentifikator(identifikator).type
                val isDeltAnsvar =
                    partIdType == PartIdType.VIRKSOMHET && eregClient.hentNoekkelInformasjonOmOrganisasjon(identifikator)
                        .isDeltAnsvar()

                val defaultUtsendingskanal = dokDistKanalService.getUtsendingskanal(
                    mottakerId = identifikator,
                    brukerId = sakenGjelderFnr,
                    tema = tema,
                    saksbehandlerContext = !systemContext,
                )

                if (isDeltAnsvar) {
                    false to true
                } else if (defaultUtsendingskanal == BehandlingDetaljerView.Utsendingskanal.SENTRAL_UTSKRIFT && isAddressOverridden) {
                    false to true
                } else {
                    false to false
                }
            }
        }

        HandlingEnum.LOCAL_PRINT -> true to false
        HandlingEnum.CENTRAL_PRINT -> false to true
    }

    fun getDokumentUnderArbeidAdresse(
        overrideAddress: AddressInput?,
        getAddressFromFullmektig: Boolean,
        fullmektig: Prosessfullmektig?,
    ): Adresse? {
        return if (overrideAddress != null) {
            val poststed = if (overrideAddress.landkode == "NO") {
                if (overrideAddress.postnummer != null) {
                    kodeverkService.getPoststed(overrideAddress.postnummer)
                } else null
            } else null

            Adresse(
                adresselinje1 = overrideAddress.adresselinje1,
                adresselinje2 = overrideAddress.adresselinje2,
                adresselinje3 = overrideAddress.adresselinje3,
                postnummer = overrideAddress.postnummer,
                poststed = poststed,
                landkode = overrideAddress.landkode
            )
        } else if (getAddressFromFullmektig) {
            Adresse(
                adresselinje1 = fullmektig!!.address!!.adresselinje1,
                adresselinje2 = fullmektig.address.adresselinje2,
                adresselinje3 = fullmektig.address.adresselinje3,
                postnummer = fullmektig.address.postnummer,
                poststed = fullmektig.address.poststed,
                landkode = fullmektig.address.landkode,
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
        val dokumentUnderArbeid = getDokumentUnderArbeid(dokumentId)

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(dokumentUnderArbeid.behandlingId)

        val duration = measureTime {
            documentPolicyService.validateDokumentUnderArbeidAction(
                behandling = behandling,
                dokumentType = documentPolicyService.getDokumentType(dokumentId),
                parentDokumentType = documentPolicyService.getParentDokumentType(parentDuaId = if (dokumentUnderArbeid is DokumentUnderArbeidAsVedlegg) dokumentUnderArbeid.parentId else null),
                documentRole = dokumentUnderArbeid.creatorRole,
                action = DuaAccessPolicy.Action.RENAME,
                duaMarkertFerdig = dokumentUnderArbeid.erMarkertFerdig(),
            )
        }
        logger.debug(
            "Validated updateDokumentTitle action. Duration: {} ms",
            duration.inWholeMilliseconds
        )

        if (dokumentTitle.length > MAX_NAME_LENGTH) {
            throw DokumentValidationException("Dokumentnavnet kan ikke være lenger enn $MAX_NAME_LENGTH tegn")
        }

        dokumentUnderArbeid.name = dokumentTitle

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
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

    fun updateSmartdokumentLanguage(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        language: Language,
        innloggetIdent: String
    ): DokumentUnderArbeid {
        val dokumentUnderArbeid = getDokumentUnderArbeid(dokumentId)

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(dokumentUnderArbeid.behandlingId)

        val behandlingRole = behandling.getRoleInBehandling(innloggetIdent)

        if (behandling.ferdigstilling == null) {
            if (dokumentUnderArbeid.creatorRole != behandlingRole && !innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter()) {
                throw MissingTilgangException("$behandlingRole har ikke anledning til å endre språk på dette dokumentet eiet av ${dokumentUnderArbeid.creatorRole}.")
            }
        }

        if (dokumentUnderArbeid.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke endre språk på et dokument som er ferdigstilt")
        }

        dokumentUnderArbeid as DokumentUnderArbeidAsSmartdokument

        dokumentUnderArbeid.language = language

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                SmartDocumentChangedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    document = SmartDocumentChangedEvent.SmartDocumentChanged(
                        id = dokumentUnderArbeid.id.toString(),
                        language = DokumentView.Language.valueOf(dokumentUnderArbeid.language.name),
                    ),
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.SMART_DOCUMENT_LANGUAGE,
        )

        return dokumentUnderArbeid
    }

    fun validateWriteAccessToSmartDocument(
        dokumentId: UUID,
        behandling: Behandling,
    ) {
        val dokument = getDokumentUnderArbeid(dokumentId)

        val duration = measureTime {
            documentPolicyService.validateDokumentUnderArbeidAction(
                behandling = behandling,
                dokumentType = documentPolicyService.getDokumentType(duaId = dokumentId),
                parentDokumentType = documentPolicyService.getParentDokumentType(parentDuaId = if (dokument is DokumentUnderArbeidAsVedlegg) dokument.parentId else null),
                documentRole = dokument.creatorRole,
                action = DuaAccessPolicy.Action.WRITE,
                duaMarkertFerdig = dokument.erMarkertFerdig(),
            )
        }
        logger.debug(
            "Validated validateWriteAccessToSmartDocument action. Duration: {} ms",
            duration.inWholeMilliseconds
        )
    }

    private fun getVedlegg(hoveddokumentId: UUID): Set<DokumentUnderArbeidAsVedlegg> {
        return dokumentUnderArbeidCommonService.findVedleggByParentId(hoveddokumentId)
    }

    private fun validatePlaceholdersInSingleSmartDocument(dokument: DokumentUnderArbeidAsSmartdokument): DocumentValidationResponse {
        logger.debug("Getting json document, dokumentId: {}", dokument.id)
        val documentJson = smartEditorApiGateway.getDocumentAsJson(dokument.smartEditorId)
        logger.debug("Validating json document in kabalJsonToPdf, dokumentId: {}", dokument.id)
        val response = kabalJsonToPdfService.validateJsonDocument(documentJson)
        return DocumentValidationResponse(
            dokumentId = dokument.id,
            errors = response.errors.map {
                when (it.type) {
                    "EMPTY_PLACEHOLDERS" -> {
                        DocumentValidationResponse.DocumentValidationError(
                            type = DocumentValidationResponse.DocumentValidationError.SmartDocumentErrorType.EMPTY_PLACEHOLDER,
                        )
                    }

                    "EMPTY_REGELVERK" -> {
                        DocumentValidationResponse.DocumentValidationError(
                            type = DocumentValidationResponse.DocumentValidationError.SmartDocumentErrorType.EMPTY_REGELVERK,
                        )
                    }

                    else -> error("Unknown error type: ${it.type}")
                }
            }
        )
    }

    fun finnOgMarkerFerdigHovedDokument(
        behandlingId: UUID,
        dokumentId: UUID,
        utfoerendeIdent: String,
        systemContext: Boolean,
    ): DokumentUnderArbeidAsHoveddokument {
        val hovedDokument = getDokumentUnderArbeid(dokumentId)

        if (hovedDokument !is DokumentUnderArbeidAsHoveddokument) {
            throw RuntimeException("document is not hoveddokument")
        }

        if (hovedDokument.dokumentType == DokumentType.EKSPEDISJONSBREV_TIL_TRYGDERETTEN && activeSpringProfile != "dev") {
            throw DokumentValidationException("Ekspedisjonsbrev til Trygderetten er ikke tilgjengelig i prod enda.")
        }

        val behandling = if (systemContext) {
            behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(hovedDokument.behandlingId)
        } else {
            behandlingService.getBehandlingAndCheckLeseTilgangForPerson(hovedDokument.behandlingId)
        }

        val duration = measureTime {
            documentPolicyService.validateDokumentUnderArbeidAction(
                behandling = behandling,
                dokumentType = documentPolicyService.getDokumentType(duaId = dokumentId),
                parentDokumentType = documentPolicyService.getParentDokumentType(parentDuaId = null),
                documentRole = hovedDokument.creatorRole,
                action = DuaAccessPolicy.Action.FINISH,
                duaMarkertFerdig = hovedDokument.erMarkertFerdig(),
                isSystemContext = systemContext,
            )
        }
        logger.debug(
            "Validated finnOgMarkerFerdigHovedDokument action. Duration: {} ms",
            duration.inWholeMilliseconds
        )

        hovedDokument.journalfoerendeEnhetId = if (systemContext) {
            "9999"
        } else {
            saksbehandlerService.getEnhetForSaksbehandler(
                utfoerendeIdent
            ).enhetId
        }

        if (hovedDokument.dokumentType in listOf(
                DokumentType.KJENNELSE_FRA_TRYGDERETTEN,
                DokumentType.EKSPEDISJONSBREV_TIL_TRYGDERETTEN
            )
        ) {
            hovedDokument.brevmottakere.clear()
            hovedDokument.brevmottakere.add(
                Brevmottaker(
                    //Hardkoder Trygderetten
                    technicalPartId = UUID.randomUUID(),
                    identifikator = organisasjonsnummerTrygderetten,
                    localPrint = false,
                    forceCentralPrint = false,
                    address = null,
                    navn = null,
                )
            )
        }

        validateDocumentBeforeFerdig(
            hovedDokument = hovedDokument,
            systemContext = systemContext,
        )

        val vedlegg = getVedlegg(hovedDokument.id)

        if (hovedDokument is SmartdokumentUnderArbeidAsHoveddokument) {
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

        val now = LocalDateTime.now()
        hovedDokument.markerFerdigHvisIkkeAlleredeMarkertFerdig(tidspunkt = now, saksbehandlerIdent = utfoerendeIdent)

        vedlegg.forEach {
            it.markerFerdigHvisIkkeAlleredeMarkertFerdig(
                tidspunkt = now,
                saksbehandlerIdent = utfoerendeIdent
            )
        }

        if (vedlegg.isNotEmpty() && !hovedDokument.isInngaaende()) {
            innholdsfortegnelseService.saveInnholdsfortegnelse(
                dokumentUnderArbeid = hovedDokument,
                fnr = behandling.sakenGjelder.partId.value,
            )
        }

        applicationEventPublisher.publishEvent(DokumentFerdigstiltAvSaksbehandler(hovedDokument))

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                DocumentsChangedEvent(
                    actor = Employee(
                        navIdent = utfoerendeIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeIdent),
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

        if (hovedDokument is SmartdokumentUnderArbeidAsHoveddokument) {
            applicationEventPublisher.publishEvent(SmartDocumentAccessDocumentEvent(dokumentId))
        }

        vedlegg.forEach {
            if (it is SmartdokumentUnderArbeidAsVedlegg) {
                applicationEventPublisher.publishEvent(SmartDocumentAccessDocumentEvent(it.id))
            }
        }

        return hovedDokument
    }

    fun validateDokumentUnderArbeidAndVedlegg(dokumentUnderArbeidId: UUID): List<DocumentValidationResponse> {
        val dokumentUnderArbeid = getDokumentUnderArbeid(dokumentUnderArbeidId)
                as DokumentUnderArbeidAsHoveddokument
        return validateDokumentUnderArbeidAndVedlegg(
            dokumentUnderArbeid = dokumentUnderArbeid,
            systemContext = false
        )
    }

    private fun validateDokumentUnderArbeidAndVedlegg(
        dokumentUnderArbeid: DokumentUnderArbeidAsHoveddokument,
        systemContext: Boolean
    ): List<DocumentValidationResponse> {

        val behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(
            behandlingId = dokumentUnderArbeid.behandlingId
        )

        val errors = mutableListOf<DocumentValidationResponse>()

        dokumentUnderArbeid.brevmottakere.forEach { mottaker ->
            if (mottaker.identifikator != null) {
                val part = partSearchService.searchPartWithUtsendingskanal(
                    identifikator = mottaker.identifikator,
                    systemUserContext = true,
                    sakenGjelderId = behandling.sakenGjelder.partId.value,
                    tema = behandling.ytelse.toTema(),
                    systemContext = systemContext,
                )

                if (documentWillGoToCentralPrint(mottaker, part)) {
                    if (mottaker.address == null && part.address == null) {
                        errors += DocumentValidationResponse(
                            dokumentId = dokumentUnderArbeid.id,
                            errors = listOf(
                                DocumentValidationResponse.DocumentValidationError(
                                    type = DocumentValidationResponse.DocumentValidationError.SmartDocumentErrorType.INVALID_RECIPIENT,
                                )
                            )
                        )
                    }
                }

                when (part.type) {
                    BehandlingDetaljerView.IdType.FNR -> if (part.statusList.any { it.status == BehandlingDetaljerView.PartStatus.Status.DEAD }) {
                        errors += DocumentValidationResponse(
                            dokumentId = dokumentUnderArbeid.id,
                            errors = listOf(
                                DocumentValidationResponse.DocumentValidationError(
                                    type = DocumentValidationResponse.DocumentValidationError.SmartDocumentErrorType.INVALID_RECIPIENT,
                                )
                            )
                        )
                    }

                    BehandlingDetaljerView.IdType.ORGNR -> if (part.statusList.any { it.status == BehandlingDetaljerView.PartStatus.Status.DELETED }) {
                        errors += DocumentValidationResponse(
                            dokumentId = dokumentUnderArbeid.id,
                            errors = listOf(
                                DocumentValidationResponse.DocumentValidationError(
                                    type = DocumentValidationResponse.DocumentValidationError.SmartDocumentErrorType.INVALID_RECIPIENT,
                                )
                            )
                        )
                    }

                    else -> {
                        error("Missing type in part")
                    }
                }
            }
        }

        val vedlegg = getVedlegg(dokumentUnderArbeid.id)

        (vedlegg + dokumentUnderArbeid).forEach { document ->
            if (document is DokumentUnderArbeidAsSmartdokument) {
                if (document.mellomlagretDate != null && document.mellomlagretDate!!.toLocalDate() != LocalDate.now()) {
                    errors += DocumentValidationResponse(
                        dokumentId = document.id,
                        errors = listOf(
                            DocumentValidationResponse.DocumentValidationError(
                                type = DocumentValidationResponse.DocumentValidationError.SmartDocumentErrorType.WRONG_DATE,
                            )
                        )
                    )
                } else if (document.isPDFGenerationNeeded()) {
                    errors += DocumentValidationResponse(
                        dokumentId = document.id,
                        errors = listOf(
                            DocumentValidationResponse.DocumentValidationError(
                                type = DocumentValidationResponse.DocumentValidationError.SmartDocumentErrorType.DOCUMENT_MODIFIED,
                            )
                        )
                    )
                }

                errors += validatePlaceholdersInSingleSmartDocument(document)
            }
        }

        return errors.groupBy { it.dokumentId }.map { (key, value) ->
            val errorsPerDocument = value.flatMap { it.errors }
            DocumentValidationResponse(
                dokumentId = key,
                errors = errorsPerDocument,
            )
        }

    }

    private fun documentWillGoToCentralPrint(
        mottaker: Brevmottaker,
        part: BehandlingDetaljerView.SearchPartViewWithUtsendingskanal
    ): Boolean {
        return mottaker.forceCentralPrint ||
                (!mottaker.localPrint && part.utsendingskanal == BehandlingDetaljerView.Utsendingskanal.SENTRAL_UTSKRIFT)
    }

    private fun validateDocumentBeforeFerdig(
        hovedDokument: DokumentUnderArbeidAsHoveddokument,
        systemContext: Boolean,
    ) {
        val errors = validateDokumentUnderArbeidAndVedlegg(
            dokumentUnderArbeid = hovedDokument,
            systemContext = systemContext,
        )
        if (errors.any { it.errors.isNotEmpty() }) {
            logger.error("Error in validateDokumentUnderArbeidAndVedlegg: ${errors.joinToString()}")
            throw SmartDocumentValidationException(
                msg = "Dokument(er) med valideringsfeil.",
                errors = errors,
            )
        }

        val avsenderMottakerInfoSet = hovedDokument.brevmottakere

        if (hovedDokument.dokumentType !in listOf(
                DokumentType.NOTAT,
                DokumentType.EKSPEDISJONSBREV_TIL_TRYGDERETTEN
            ) && avsenderMottakerInfoSet.isEmpty()
        ) {
            throw DokumentValidationException("Avsender/mottakere må være satt")
        }

        if (hovedDokument.dokumentType == DokumentType.ANNEN_INNGAAENDE_POST && (hovedDokument as OpplastetDokumentUnderArbeidAsHoveddokument).inngaaendeKanal == null) {
            throw DokumentValidationException("Trenger spesifisert inngående kanal for ${hovedDokument.dokumentType.navn}.")
        }
    }

    fun getInnholdsfortegnelseAsFysiskDokument(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        hoveddokumentId: UUID,
        innloggetIdent: String
    ): ResponseEntity<ByteArray> {
        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        val dokument =
            getDokumentUnderArbeid(hoveddokumentId) as DokumentUnderArbeidAsHoveddokument
        if (dokument.dokumentType.isInngaaende()) {
            throw DokumentValidationException("${dokument.dokumentType.navn} støtter ikke vedleggsoversikt.")
        }

        val filename = "vedleggsoversikt til \"${dokument.name}\", ${LocalDate.now().format(DATE_FORMAT)}"

        return ResponseEntity(
            innholdsfortegnelseService.getInnholdsfortegnelseAsPdf(
                dokumentUnderArbeid = dokument,
                fnr = behandling.sakenGjelder.partId.value,
            ),
            HttpHeaders().apply {
                contentType = MediaType.APPLICATION_PDF
                add(
                    "Content-Disposition",
                    "inline; filename=\"$filename.pdf\""
                )
            },
            HttpStatus.OK
        )
    }

    fun getFysiskDokumentAsResourceOrUrl(
        behandlingId: UUID, //Kan brukes i finderne for å "være sikker", men er egentlig overflødig..
        dokumentId: UUID,
        innloggetIdent: String,
        variantFormat: DokumentReferanse.Variant.Format
    ): Triple<String, Any, MediaType?> {
        val dokumentUnderArbeid = getDokumentUnderArbeid(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(dokumentUnderArbeid.behandlingId)

        val (title, resourceOrLink, mediaType) = if (dokumentUnderArbeid.erFerdigstilt()) {
            if (dokumentUnderArbeid.dokarkivReferences.isEmpty()) {
                throw RuntimeException("Dokument is finalized but has no dokarkiv references")
            }

            val dokarkivReference = dokumentUnderArbeid.dokarkivReferences.first()
            val fysiskDokument = dokumentService.getFysiskDokument(
                journalpostId = dokarkivReference.journalpostId,
                dokumentInfoId = dokarkivReference.dokumentInfoId!!,
                variantFormat = variantFormat,
            )
            Triple(
                fysiskDokument.title,
                fysiskDokument.content,
                fysiskDokument.mediaType
            )
        } else {
            when (dokumentUnderArbeid) {
                is OpplastetDokumentUnderArbeidAsHoveddokument -> {
                    Triple(
                        dokumentUnderArbeid.name,
                        mellomlagerService.getUploadedDocumentAsSignedURL(dokumentUnderArbeid.mellomlagerId!!),
                        null
                    )
                }

                is OpplastetDokumentUnderArbeidAsVedlegg -> {
                    Triple(
                        dokumentUnderArbeid.name,
                        mellomlagerService.getUploadedDocumentAsSignedURL(dokumentUnderArbeid.mellomlagerId!!),
                        null
                    )
                }

                is DokumentUnderArbeidAsSmartdokument -> {
                    if (dokumentUnderArbeid.isPDFGenerationNeeded()) {
                        Triple(
                            dokumentUnderArbeid.name,
                            ByteArrayResource(
                                mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(
                                    dokumentUnderArbeid
                                ).bytes
                            ),
                            MediaType.APPLICATION_PDF
                        )
                    } else Triple(
                        dokumentUnderArbeid.name,
                        mellomlagerService.getUploadedDocumentAsSignedURL(dokumentUnderArbeid.mellomlagerId!!),
                        null
                    )
                }

                is JournalfoertDokumentUnderArbeidAsVedlegg -> {
                    val journalpost = safFacade.getJournalpostAsSaksbehandler(
                        journalpostId = dokumentUnderArbeid.journalpostId,
                    )

                    val dokument = journalpost.dokumenter?.find { it.dokumentInfoId == dokumentUnderArbeid.dokumentInfoId }
                        ?: throw RuntimeException("Document not found in Dokarkiv")
                    if (!dokumentMapper.harTilgangTilArkivEllerSladdetVariant(dokument)) {
                        throw NoAccessToDocumentException("Kan ikke vise dokument med journalpostId ${journalpost.journalpostId}, dokumentInfoId ${dokument.dokumentInfoId}. Mangler tilgang til tema ${journalpost.tema} i dokumentarkivet.")
                    }

                    val fysiskDokument = dokumentService.getFysiskDokument(
                        journalpostId = dokumentUnderArbeid.journalpostId,
                        dokumentInfoId = dokumentUnderArbeid.dokumentInfoId,
                        variantFormat = variantFormat,
                    )
                    Triple(
                        fysiskDokument.title,
                        fysiskDokument.content,
                        fysiskDokument.mediaType
                    )
                }

                else -> {
                    error("can't come here")
                }
            }
        }

        return Triple(
            title,
            resourceOrLink,
            mediaType
        )
    }

    fun slettDokument(
        dokumentId: UUID,
        innloggetIdent: String,
    ) {
        val document = getDokumentUnderArbeid(dokumentId)

        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(
            behandlingId = document.behandlingId,
        )

        val parentDocumentId: UUID?
        val vedleggList: List<DokumentUnderArbeidAsVedlegg>
        if (document is DokumentUnderArbeidAsVedlegg) {
            parentDocumentId = document.parentId
            vedleggList = emptyList()
        } else {
            parentDocumentId = null
            vedleggList = dokumentUnderArbeidCommonService.findVedleggByParentId(dokumentId).toList()
        }

        //first vedlegg
        if (vedleggList.isNotEmpty()) {
            val parentDokumentType = documentPolicyService.getParentDokumentType(parentDuaId = dokumentId)
            vedleggList.forEach { vedlegg ->
                val duration = measureTime {
                    documentPolicyService.validateDokumentUnderArbeidAction(
                        behandling = behandling,
                        dokumentType = documentPolicyService.getDokumentType(duaId = vedlegg.id),
                        parentDokumentType = parentDokumentType,
                        documentRole = vedlegg.creatorRole,
                        action = DuaAccessPolicy.Action.REMOVE,
                        duaMarkertFerdig = vedlegg.erMarkertFerdig(),
                    )
                }
                logger.debug(
                    "Validated remove vedlegg action. Duration: {} ms",
                    duration.inWholeMilliseconds
                )
            }
        }

        //then actual document
        val duration = measureTime {
            documentPolicyService.validateDokumentUnderArbeidAction(
                behandling = behandling,
                dokumentType = documentPolicyService.getDokumentType(duaId = dokumentId),
                parentDokumentType = documentPolicyService.getParentDokumentType(parentDuaId = parentDocumentId),
                documentRole = document.creatorRole,
                action = DuaAccessPolicy.Action.REMOVE,
                duaMarkertFerdig = document.erMarkertFerdig(),
            )
        }
        logger.debug(
            "Validated remove hoveddokument action. Duration: {} ms",
            duration.inWholeMilliseconds
        )

        val documentsToRemove = vedleggList + document
        deleteDocuments(documents = documentsToRemove)

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                DocumentsRemovedEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    idList = documentsToRemove.map { it.id.toString() },
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.DOCUMENTS_REMOVED,
        )

        documentsToRemove.forEach { dua ->
            if (dua is DokumentUnderArbeidAsSmartdokument) {
                applicationEventPublisher.publishEvent(SmartDocumentAccessDocumentEvent(dua.id))
            }
        }
    }

    private fun deleteDocuments(documents: List<DokumentUnderArbeid>) {
        documents.forEach { document ->
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

        dokumentUnderArbeidRepository.deleteAll(documents)
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
            getDokumentUnderArbeid(newParentId) as DokumentUnderArbeidAsHoveddokument

        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(
            behandlingId = parentDocument.behandlingId,
        )

        if (parentDocument.erMarkertFerdig()) {
            throw DokumentValidationException("Kan ikke koble til et dokument som er ferdigstilt")
        }
        val currentDocument = getDokumentUnderArbeid(dokumentId)

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
            getDokumentUnderArbeid(currentDokumentId)

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

    fun getSvarbrevAsOpplastetDokumentUnderArbeidAsHoveddokument(behandlingId: UUID): OpplastetDokumentUnderArbeidAsHoveddokument? {
        //Sjekker tilgang på behandlingsnivå:
        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        return opplastetDokumentUnderArbeidAsHoveddokumentRepository.findByBehandlingIdAndMarkertFerdigNotNull(
            behandlingId
        ).find { it.dokumentType == DokumentType.SVARBREV }
    }

    fun getDokumentUnderArbeidView(dokumentUnderArbeidId: UUID, behandlingId: UUID): DokumentView {
        //Sjekker tilgang på behandlingsnivå:
        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        val dokumentUnderArbeid = getDokumentUnderArbeid(dokumentUnderArbeidId)
        return getDokumentViewList(
            dokumentUnderArbeidList = listOf(dokumentUnderArbeid),
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
                    smartEditorApiGateway.getSmartDocumentResponse(smartEditorId = it.smartEditorId)
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
            getDokumentUnderArbeid(hovedDokumentId) as DokumentUnderArbeidAsHoveddokument
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
            getDokumentUnderArbeid(hovedDokumentId) as DokumentUnderArbeidAsHoveddokument
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
            val saksbehandlerIdent = hovedDokument.markertFerdigBy!!
            val saksdokumenter = journalpost.mapToSaksdokumenter()

            if (behandling.ferdigstilling == null) {
                saksdokumenter.forEach { saksdokument ->
                    behandling.addSaksdokument(saksdokument, saksbehandlerIdent)
                        ?.also { applicationEventPublisher.publishEvent(it) }
                }

                publishInternalEvent(
                    data = jacksonObjectMapper.writeValueAsString(
                        IncludedDocumentsChangedEvent(
                            actor = Employee(
                                navIdent = saksbehandlerIdent,
                                navn = saksbehandlerService.getNameForIdentDefaultIfNull(saksbehandlerIdent),
                            ),
                            timestamp = LocalDateTime.now(),
                            journalfoertDokumentReferenceSet = saksdokumenter.map {
                                JournalfoertDokument(
                                    it.journalpostId,
                                    it.dokumentInfoId
                                )
                            }.toSet()

                        )
                    ),
                    behandlingId = behandling.id,
                    type = InternalEventType.INCLUDED_DOCUMENTS_ADDED,
                )
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

    fun mergeDUAAndCreatePDF(dokumentUnderArbeidId: UUID): Pair<FileSystemResource, String> {
        val dokumentUnderArbeid = getDokumentUnderArbeid(dokumentUnderArbeidId) as DokumentUnderArbeidAsHoveddokument

        val hoveddokumentPDFResource: Resource = if (dokumentUnderArbeid is SmartdokumentUnderArbeidAsHoveddokument) {
            if (dokumentUnderArbeid.isPDFGenerationNeeded()) {
                ByteArrayResource(mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(dokumentUnderArbeid).bytes)
            } else {
                mellomlagerService.getUploadedDocument(dokumentUnderArbeid.mellomlagerId!!)
            }
        } else {
            dokumentUnderArbeid as OpplastetDokumentUnderArbeidAsHoveddokument
            mellomlagerService.getUploadedDocument(dokumentUnderArbeid.mellomlagerId!!)
        }

        val vedleggList = getVedlegg(dokumentUnderArbeidId)

        val vedleggAsResourceList: List<Resource> =
            vedleggList.sortedByDescending { it.created }.mapNotNull { vedlegg ->
                when (vedlegg) {
                    is SmartdokumentUnderArbeidAsVedlegg -> {
                        if (vedlegg.isPDFGenerationNeeded()) {
                            ByteArrayResource(mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(vedlegg).bytes)
                        } else {
                            mellomlagerService.getUploadedDocument(vedlegg.mellomlagerId!!)
                        }
                    }

                    is OpplastetDokumentUnderArbeidAsVedlegg -> {
                        mellomlagerService.getUploadedDocument(vedlegg.mellomlagerId!!)
                    }

                    else -> null
                }
            }

        val innholdsfortegnelsePath = if (vedleggList.isNotEmpty() && !dokumentUnderArbeid.isInngaaende()) {
            val innholdsfortegnelsePDFBytes = innholdsfortegnelseService.getInnholdsfortegnelseAsPdf(
                dokumentUnderArbeid = dokumentUnderArbeid,
                fnr = behandlingService.getBehandlingForReadWithoutCheckForAccess(dokumentUnderArbeid.behandlingId).sakenGjelder.partId.value
            )
            Files.write(Files.createTempFile("", ""), innholdsfortegnelsePDFBytes)
        } else {
            null
        }

        val journalfoerteVedlegg = vedleggList.filterIsInstance<JournalfoertDokumentUnderArbeidAsVedlegg>()

        if (journalfoerteVedlegg.isNotEmpty()) {
            val behandling = behandlingService.getBehandlingForReadWithoutCheckForAccess(dokumentUnderArbeid.behandlingId)
            val journalpostListFromSaf = safFacade.getJournalposter(
                journalpostIdSet = journalfoerteVedlegg.map { it.journalpostId }.toSet(),
                fnr = behandling.sakenGjelder.partId.value,
                saksbehandlerContext = true,
            )
            journalfoerteVedlegg.forEach { journalfoerteVedlegg ->
                val journalpost = journalpostListFromSaf.find { it.journalpostId == journalfoerteVedlegg.journalpostId }
                val dokument = journalpost?.dokumenter?.find { it.dokumentInfoId == journalfoerteVedlegg.dokumentInfoId }
                    ?: throw RuntimeException("Document not found in Dokarkiv")
                if (!dokumentMapper.harTilgangTilArkivEllerSladdetVariant(dokument)) {
                    throw NoAccessToDocumentException("Kan ikke vise dokument med journalpostId ${journalpost.journalpostId}, dokumentInfoId ${dokument.dokumentInfoId}. Mangler tilgang til tema ${journalpost.tema} i dokumentarkivet.")
                }
            }
        }

        val journalfoertePath = if (journalfoerteVedlegg.isNotEmpty()) {
            dokumentService.mergeJournalfoerteDocuments(
                documentsToMerge = journalfoerteVedlegg
                    .sortedBy { it.sortKey }
                    .map {
                        it.journalpostId to it.dokumentInfoId
                    },
                preferArkivvariantIfAccess = false,
            ).first
        } else {
            null
        }

        val filesToMerge = mutableListOf<Resource>()

        //Add files in correct order
        filesToMerge.add(hoveddokumentPDFResource)

        if (innholdsfortegnelsePath != null) {
            filesToMerge.add(FileSystemResource(innholdsfortegnelsePath))
        }

        filesToMerge.addAll(vedleggAsResourceList)

        if (journalfoertePath != null) {
            filesToMerge.add(FileSystemResource(journalfoertePath))
        }

        return dokumentService.mergePDFFiles(resourcesToMerge = filesToMerge, title = "Samlet dokument")
    }

    private fun mellomlagreNyVersjonAvSmartEditorDokumentAndGetPdf(dokumentUnderArbeid: DokumentUnderArbeid): PDFDocument {
        if (dokumentUnderArbeid !is DokumentUnderArbeidAsSmartdokument) {
            throw RuntimeException("dokument is not smartdokument")
        }

        val smartDocument = smartEditorApiGateway.getSmartDocumentResponse(dokumentUnderArbeid.smartEditorId)
        val pdfDocument = kabalJsonToPdfService.getPDFDocument(smartDocument.json)

        val mellomlagerId =
            mellomlagerService.uploadResource(
                resource = ByteArrayResource(pdfDocument.bytes),
            )
        val mellomlagretDate = LocalDateTime.now()

        if (dokumentUnderArbeid.mellomlagerId != null) {
            mellomlagerService.deleteDocument(dokumentUnderArbeid.mellomlagerId!!)
        }

        dokumentUnderArbeid.mellomlagerId = mellomlagerId
        dokumentUnderArbeid.mellomlagretDate = mellomlagretDate
        dokumentUnderArbeid.size = pdfDocument.bytes.size.toLong()
        dokumentUnderArbeid.modified = LocalDateTime.now()
        dokumentUnderArbeid.mellomlagretVersion = smartDocument.version

        return pdfDocument
    }

    private fun DokumentUnderArbeidAsSmartdokument.isPDFGenerationNeeded(): Boolean {
        return mellomlagretDate == null ||
                mellomlagretDate!!.toLocalDate() != LocalDate.now() ||
                smartEditorApiGateway.getSmartDocumentResponse(smartEditorId).version > (mellomlagretVersion ?: -1)
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

    fun createAndFinalizeDokumentUnderArbeidFromSvarbrev(
        svarbrev: Svarbrev,
        behandling: Behandling,
        avsenderEnhetId: String,
    ): DokumentUnderArbeidAsHoveddokument {
        val bytes = kabalJsonToPdfService.getSvarbrevPDF(
            svarbrev = svarbrev,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            sakenGjelderIdentifikator = behandling.sakenGjelder.partId.value,
            sakenGjelderName = partSearchService.searchPart(
                identifikator = behandling.sakenGjelder.partId.value,
                systemUserContext = false
            ).name,
            ytelse = behandling.ytelse,
            klagerIdentifikator = behandling.klager.partId.value,
            klagerName = partSearchService.searchPart(
                identifikator = behandling.klager.partId.value,
                systemUserContext = false
            ).name,
            avsenderEnhetId = avsenderEnhetId,
        )

        val tmpFile = Files.createTempFile(null, null).toFile()
        tmpFile.writeBytes(bytes)

        val documentView = createOpplastetDokumentUnderArbeid(
            behandlingId = behandling.id,
            dokumentTypeId = DokumentType.SVARBREV.id,
            parentId = null,
            file = tmpFile,
            filename = svarbrev.title,
            utfoerendeIdent = tokenUtil.getIdent(),
            systemContext = false,
        )

        updateMottakere(
            behandlingId = behandling.id,
            dokumentId = documentView.id,
            mottakerInput = MottakerInput(
                svarbrev.receivers.map {
                    Mottaker(
                        id = UUID.randomUUID(),
                        identifikator = it.identifikator,
                        handling = HandlingEnum.valueOf(it.handling.name),
                        overriddenAddress = it.overriddenAddress?.let { address ->
                            AddressInput(
                                adresselinje1 = address.adresselinje1,
                                adresselinje2 = address.adresselinje2,
                                adresselinje3 = address.adresselinje3,
                                landkode = address.landkode,
                                postnummer = address.postnummer,
                            )
                        },
                        navn = it.navn,
                    )
                }
            ),
            utfoerendeIdent = tokenUtil.getIdent(),
            systemContext = false,
        )

        val hovedDokument = finnOgMarkerFerdigHovedDokument(
            behandlingId = behandling.id,
            dokumentId = documentView.id,
            utfoerendeIdent = tokenUtil.getIdent(),
            systemContext = false,
        )

        return hovedDokument
    }

    fun createAndFinalizeForlengetBehandlingstidDokumentUnderArbeid(
        forlengetBehandlingstidDraft: ForlengetBehandlingstidDraft,
        behandling: Behandling,
    ): DokumentUnderArbeidAsHoveddokument {
        val sakenGjelderName = partSearchService.searchPart(
            identifikator = behandling.sakenGjelder.partId.value,
            systemUserContext = false
        ).name

        val bytes = kabalJsonToPdfService.getForlengetBehandlingstidPDF(
            title = forlengetBehandlingstidDraft.title!!,
            sakenGjelderName = sakenGjelderName,
            sakenGjelderIdentifikator = behandling.sakenGjelder.partId.value,
            klagerIdentifikator = behandling.klager.partId.value,
            klagerName = if (behandling.klager.partId.value != behandling.sakenGjelder.partId.value) {
                partSearchService.searchPart(
                    identifikator = behandling.klager.partId.value,
                    systemUserContext = false
                ).name
            } else {
                sakenGjelderName
            },
            ytelse = behandling.ytelse,
            fullmektigFritekst = forlengetBehandlingstidDraft.fullmektigFritekst,
            behandlingstidUnits = forlengetBehandlingstidDraft.varsletBehandlingstidUnits,
            behandlingstidUnitType = forlengetBehandlingstidDraft.varsletBehandlingstidUnitType,
            behandlingstidDate = forlengetBehandlingstidDraft.varsletFrist,
            avsenderEnhetId = Enhet.E4291.navn,
            type = behandling.type,
            mottattKlageinstans = behandling.mottattKlageinstans.toLocalDate(),
            previousBehandlingstidInfo = forlengetBehandlingstidDraft.previousBehandlingstidInfo,
            reason = forlengetBehandlingstidDraft.reason,
            customText = forlengetBehandlingstidDraft.customText,
        )

        val tmpFile = Files.createTempFile(null, null).toFile()
        tmpFile.writeBytes(bytes)

        val documentView = createOpplastetDokumentUnderArbeid(
            behandlingId = behandling.id,
            dokumentTypeId = DokumentType.FORLENGET_BEHANDLINGSTIDSBREV.id,
            parentId = null,
            file = tmpFile,
            filename = forlengetBehandlingstidDraft.title,
            utfoerendeIdent = tokenUtil.getIdent(),
            systemContext = false
        )

        val document = getDokumentUnderArbeid(documentView.id) as DokumentUnderArbeidAsHoveddokument

        forlengetBehandlingstidDraft.receivers.forEach {
            document.brevmottakere.add(
                Brevmottaker(
                    technicalPartId = it.id,
                    identifikator = it.identifikator,
                    localPrint = it.localPrint,
                    forceCentralPrint = it.forceCentralPrint,
                    address = it.address,
                    navn = it.navn,
                )
            )
        }

        val hovedDokument = finnOgMarkerFerdigHovedDokument(
            behandlingId = behandling.id,
            dokumentId = documentView.id,
            utfoerendeIdent = tokenUtil.getIdent(),
            systemContext = false
        )

        return hovedDokument
    }

    fun getInnholdsfortegnelseMetadata(
        behandlingId: UUID,
        dokumentId: UUID,
    ): DokumentUnderArbeidMetadata {
        val dokument =
            getDokumentUnderArbeid(dokumentId) as DokumentUnderArbeidAsHoveddokument

        val title = "Vedleggsoversikt til \"${dokument.name}\", ${LocalDate.now().format(DATE_FORMAT)}"

        return DokumentUnderArbeidMetadata(
            behandlingId = behandlingId,
            documentId = dokumentId,
            title = title
        )
    }
}