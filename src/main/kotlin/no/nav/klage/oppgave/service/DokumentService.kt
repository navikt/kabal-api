package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.dokument.domain.FysiskDokument
import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.view.DokumentReferanse
import no.nav.klage.oppgave.api.view.DokumenterResponse
import no.nav.klage.oppgave.api.view.JournalfoertDokumentMetadata
import no.nav.klage.oppgave.api.view.LogiskVedleggResponse
import no.nav.klage.oppgave.clients.dokarkiv.DokarkivClient
import no.nav.klage.oppgave.clients.dokarkiv.UpdateDocumentTitleDokumentInput
import no.nav.klage.oppgave.clients.dokarkiv.UpdateDocumentTitlesJournalpostInput
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.clients.saf.graphql.DokumentInfo
import no.nav.klage.oppgave.clients.saf.graphql.DokumentoversiktBruker
import no.nav.klage.oppgave.clients.saf.graphql.Journalpost
import no.nav.klage.oppgave.clients.saf.graphql.Variantformat
import no.nav.klage.oppgave.clients.saf.rest.SafRestClient
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.subentities.Saksdokument
import no.nav.klage.oppgave.domain.document.DocumentToMerge
import no.nav.klage.oppgave.domain.document.MergedDocument
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.exceptions.JournalpostNotFoundException
import no.nav.klage.oppgave.repositories.MergedDocumentRepository
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.io.RandomAccessStreamCache.StreamCacheCreateFunction
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.*
import kotlin.system.measureTimeMillis


@Service
@Transactional
class DokumentService(
    private val safRestClient: SafRestClient,
    private val mergedDocumentRepository: MergedDocumentRepository,
    private val dokumentMapper: DokumentMapper,
    private val safFacade: SafFacade,
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val saksbehandlerService: SaksbehandlerService,
    private val pdlFacade: PdlFacade,
    private val dokarkivClient: DokarkivClient,
    private val tokenUtil: TokenUtil
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
        private val jacksonObjectMapper = jacksonObjectMapper()
    }

    fun fetchDokumentlisteForBehandling(
        behandling: Behandling,
        temaer: List<Tema>,
    ): DokumenterResponse {
        if (behandling.sakenGjelder.erPerson()) {
            val dokumentoversiktBruker: DokumentoversiktBruker =
                safFacade.getDokumentoversiktBrukerAsSaksbehandler(
                    behandling.sakenGjelder.partId.value,
                    mapTema(temaer),
                )

            val dokumentReferanseList = dokumentoversiktBruker.journalposter.map { journalpost ->
                dokumentMapper.mapJournalpostToDokumentReferanse(
                    journalpost = journalpost,
                    saksdokumenter = behandling.saksdokumenter
                )
            }

            return DokumenterResponse(
                dokumenter = dokumentReferanseList,
                pageReference = if (dokumentoversiktBruker.sideInfo.finnesNesteSide) {
                    dokumentoversiktBruker.sideInfo.sluttpeker
                } else {
                    null
                },
                antall = dokumentoversiktBruker.sideInfo.antall,
                totaltAntall = dokumentoversiktBruker.sideInfo.totaltAntall,
                sakList = dokumentReferanseList.mapNotNull { it.sak }.toSet().toList(),
                avsenderMottakerList = dokumentReferanseList.mapNotNull { it.avsenderMottaker }.toSet().toList(),
                temaIdList = dokumentReferanseList.map { it.temaId }.toSet().toList(),
                journalposttypeList = dokumentReferanseList.mapNotNull { it.journalposttype }.toSet().toList(),
                fromDate = dokumentReferanseList.minOfOrNull { it.datoOpprettet }?.toLocalDate(),
                toDate = dokumentReferanseList.maxOfOrNull { it.datoOpprettet }?.toLocalDate(),
            )
        } else {
            return DokumenterResponse(
                dokumenter = emptyList(),
                pageReference = null,
                antall = 0,
                totaltAntall = 0,
                sakList = listOf(),
                avsenderMottakerList = listOf(),
                temaIdList = listOf(),
                journalposttypeList = listOf(),
                fromDate = null,
                toDate = null,
            )
        }
    }

    private fun mapTema(temaer: List<Tema>): List<no.nav.klage.oppgave.clients.saf.graphql.Tema> =
        temaer.map { tema -> no.nav.klage.oppgave.clients.saf.graphql.Tema.valueOf(tema.name) }

    fun validateJournalpostsExistsAsSystembruker(journalpostIdList: List<String>) {
        try {
            safFacade.getJournalposter(
                journalpostIdSet = journalpostIdList.toSet(),
                fnr = null,
                saksbehandlerContext = false,
            )

        } catch (e: Exception) {
            logger.warn("Unable to find journalposts", e)
            null
        } ?: throw JournalpostNotFoundException("Journalposts not found")
    }

    fun fetchDokumentInfoIdFromJournalpost(journalpost: Journalpost): List<String> {
        return journalpost.dokumenter?.filter { harArkivVariantformat(it) }?.map { it.dokumentInfoId } ?: emptyList()
    }

    fun getFysiskDokument(
        journalpostId: String,
        dokumentInfoId: String,
        variantFormat: DokumentReferanse.Variant.Format,
    ): FysiskDokument {
        val (resource, contentType) = safRestClient.getDokument(
            dokumentInfoId = dokumentInfoId,
            journalpostId = journalpostId,
            variantFormat = variantFormat.name,
        )

        return FysiskDokument(
            title = getDocumentTitle(journalpostId = journalpostId, dokumentInfoId = dokumentInfoId),
            content = resource,
            mediaType = MediaType.valueOf(contentType)
        )
    }

    fun getDocumentTitle(journalpostId: String, dokumentInfoId: String): String {
        val journalpostInDokarkiv = safFacade.getJournalposter(
            journalpostIdSet = setOf(journalpostId),
            fnr = null,
            saksbehandlerContext = true,
        ).first()

        return journalpostInDokarkiv.dokumenter?.find { it.dokumentInfoId == dokumentInfoId }?.tittel
            ?: throw RuntimeException("Document/title not found in Dokarkiv")
    }

    fun getJournalfoertDokumentMetadata(journalpostId: String, dokumentInfoId: String): JournalfoertDokumentMetadata {
        val journalpostInDokarkiv = safFacade.getJournalposter(
            journalpostIdSet = setOf(journalpostId),
            fnr = null,
            saksbehandlerContext = true,
        ).first()

        val dokumentInfo = journalpostInDokarkiv.dokumenter?.find { it.dokumentInfoId == dokumentInfoId }
        return JournalfoertDokumentMetadata(
            journalpostId = journalpostId,
            dokumentInfoId = dokumentInfoId,
            title = dokumentInfo?.tittel
                ?: throw RuntimeException("Document/title not found in Dokarkiv"),
            harTilgangTilArkivvariant = dokumentMapper.harTilgangTilArkivEllerSladdetVariant(dokumentInfo),
            hasAccess = dokumentMapper.harTilgangTilArkivEllerSladdetVariant(dokumentInfo),
            varianter = dokumentMapper.getVarianter(dokumentInfo = dokumentInfo),
        )
    }

    @Throws(IOException::class)
    private fun getMixedMemorySettingsForPDFBox(bytes: Long): StreamCacheCreateFunction {
        return MemoryUsageSetting.setupMixed(bytes).streamCache
    }

    fun changeTitleInPDF(resource: Resource, title: String): Resource {
        try {
            val tmpFile = createFileNotYetOnDisk()

            val timeMillis = measureTimeMillis {
                val memorySettingsForPDFBox: Long = 50_000_000
                val document: PDDocument = if (resource is FileSystemResource) {
                    Loader.loadPDF(resource.file, getMixedMemorySettingsForPDFBox(memorySettingsForPDFBox))
                } else {
                    Loader.loadPDF(
                        RandomAccessReadBuffer(resource.contentAsByteArray), getMixedMemorySettingsForPDFBox(
                            memorySettingsForPDFBox
                        )
                    )
                }

                val info: PDDocumentInformation = document.documentInformation
                info.title = title
                document.isAllSecurityToBeRemoved = true
                document.save(tmpFile)
                document.close()
            }
            logger.debug("changeTitleInPDF took $timeMillis ms")

            if (resource is FileSystemResource) {
                resource.file.delete()
            }

            return FileSystemResource(tmpFile)
        } catch (e: Exception) {
            logger.warn("Unable to change titleInPDF. See more details in team-logs.")
            teamLogger.warn("Unable to change title in pdf", e)
            return resource
        }
    }

    /**
     * Create a File object pointing to temporary file path that does not yet exist on disk.
     */
    private fun createFileNotYetOnDisk(): File {
        //is there a better way to do this?
        val tmpFile = Files.createTempFile(null, null)
        val pathToFile = tmpFile.toAbsolutePath().toString()

        Files.delete(tmpFile)

        return File(pathToFile)
    }

    fun getDokumentReferanse(journalpostId: String, behandling: Behandling): DokumentReferanse {
        val journalpost = safFacade.getJournalposter(
            journalpostIdSet = setOf(journalpostId),
            fnr = null,
            saksbehandlerContext = true,
        ).first()

        return dokumentMapper.mapJournalpostToDokumentReferanse(
            journalpost = journalpost,
            saksdokumenter = behandling.saksdokumenter
        )
    }

    private fun harArkivVariantformat(dokumentInfo: DokumentInfo): Boolean =
        dokumentInfo.dokumentvarianter.any { dv ->
            dv.variantformat == Variantformat.ARKIV
        }

    fun createSaksdokumenterFromJournalpostIdList(journalpostIdList: List<String>): MutableSet<Saksdokument> {
        val saksdokumenter: MutableSet<Saksdokument> = mutableSetOf()
        val journalpostList = safFacade.getJournalposter(
            journalpostIdSet = journalpostIdList.toSet(),
            fnr = null,
            saksbehandlerContext = false,
        )
        journalpostList.forEach {
            saksdokumenter.addAll(createSaksdokument(it))
        }
        return saksdokumenter
    }

    private fun createSaksdokument(journalpost: Journalpost) =
        fetchDokumentInfoIdFromJournalpost(journalpost)
            .map { Saksdokument(journalpostId = journalpost.journalpostId, dokumentInfoId = it) }

    fun storeDocumentsForMerging(documents: List<JournalfoertDokumentReference>): MergedDocument {
        val hash = documents.joinToString(separator = "") { it.journalpostId + it.dokumentInfoId }.toMd5Hash()

        val previouslyMergedDocument = mergedDocumentRepository.findByHash(hash)
        if (previouslyMergedDocument != null) {
            return previouslyMergedDocument
        }

        return mergedDocumentRepository.save(
            MergedDocument(
                title = generateTitleForDocumentsToMerge(documents),
                documentsToMerge = documents.mapIndexed { index, it ->
                    DocumentToMerge(
                        journalpostId = it.journalpostId,
                        dokumentInfoId = it.dokumentInfoId,
                        index = index,
                    )
                }.toMutableSet(),
                hash = hash,
                created = LocalDateTime.now()
            )
        )
    }

    fun String.toMd5Hash(): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(this.toByteArray())).toString(16).padStart(32, '0')
    }

    private fun generateTitleForDocumentsToMerge(documents: List<JournalfoertDokumentReference>): String {
        val numberOfDocumentNamesToShow = 3
        val truncatedMessage = if (documents.size > numberOfDocumentNamesToShow) ", ... " + (documents.size - numberOfDocumentNamesToShow) + " til" else ""
        val documentsWeCareAbout = documents.take(numberOfDocumentNamesToShow)
        val journalpostList = safFacade.getJournalposter(
            journalpostIdSet = documentsWeCareAbout.map { it.journalpostId }.toSet(),
            fnr = null,
            saksbehandlerContext = true,
        )

        return "(${documents.size}) " + documentsWeCareAbout
            .joinToString { journalfoertDokumentReference ->
                journalpostList
                    .find { it.journalpostId == journalfoertDokumentReference.journalpostId }!!
                    .dokumenter?.find { it.dokumentInfoId == journalfoertDokumentReference.dokumentInfoId }?.tittel
                    ?: throw RuntimeException("Document/title not found in Dokarkiv")
            } + truncatedMessage
    }

    fun mergeJournalfoerteDocuments(id: UUID, preferArkivvariantIfAccess: Boolean): Pair<Path, String> {
        val mergedDocument = mergedDocumentRepository.getReferenceById(id)
        val documentsToMerge = mergedDocument.documentsToMerge.sortedBy { it.index }

        return mergeJournalfoerteDocuments(
            documentsToMerge = documentsToMerge.map { it.journalpostId to it.dokumentInfoId },
            title = mergedDocument.title,
            preferArkivvariantIfAccess = preferArkivvariantIfAccess,
        )
    }

    fun mergeJournalfoerteDocuments(
        documentsToMerge: List<Pair<String, String>>,
        title: String = "merged document",
        preferArkivvariantIfAccess: Boolean,
        journalposterSupplied: List<Journalpost>? = null,
    ): Pair<Path, String> {
        if (documentsToMerge.isEmpty()) {
            throw RuntimeException("No documents to merge")
        }

        val merger = PDFMergerUtility()

        val pdDocumentInformation = PDDocumentInformation()
        pdDocumentInformation.title = title
        merger.destinationDocumentInformation = pdDocumentInformation

        val pathToMergedDocument = createFileNotYetOnDisk()

        merger.destinationFileName = pathToMergedDocument.absolutePath

        val documentsWithPaths = documentsToMerge.map {
            val tmpFile = Files.createTempFile(null, null)
            it to tmpFile
        }

        val userToken = tokenUtil.getSaksbehandlerAccessTokenWithSafScope()

        val journalposter = journalposterSupplied
            ?: safFacade.getJournalposter(
                journalpostIdSet = documentsToMerge.map { it.first }.toSet(),
                fnr = null,
                saksbehandlerContext = true,
            )

        //Download in parallel with controlled concurrency for performance
        val concurrency = 20
        documentsWithPaths.chunked(concurrency).forEach { batch ->
            batch.parallelStream().forEach { (document, path) ->
                safRestClient.downloadDocumentAsMono(
                    journalpostId = document.first,
                    dokumentInfoId = document.second,
                    pathToFile = path,
                    token = userToken,
                    variantFormat = getPreferredVariantFormatAsString(
                        document = document,
                        journalposter = journalposter,
                        preferArkivvariantIfAccess = preferArkivvariantIfAccess,
                    )
                ).block()
            }
        }

        //Add sources after download to preserve order
        documentsWithPaths.forEach { (_, path) ->
            merger.addSource(path.toFile())
        }

        //Use mixed memory: keep small merges in memory, use disk for large ones
        merger.mergeDocuments(getMixedMemorySettingsForPDFBox(50_000_000))

        //clean tmp files that were downloaded from SAF
        try {
            documentsWithPaths.forEach { (_, pathToTmpFile) ->
                pathToTmpFile.toFile().delete()
            }
        } catch (e: Exception) {
            logger.warn("couldn't delete tmp files", e)
        }

        return pathToMergedDocument.toPath() to title
    }

    private fun getPreferredVariantFormatAsString(
        document: Pair<String, String>,
        journalposter: List<Journalpost>,
        preferArkivvariantIfAccess: Boolean,
    ): String {
        val journalpost = journalposter.find { it.journalpostId == document.first }
            ?: throw RuntimeException("Document not found in SAF")
        val dokumentInfo = journalpost.dokumenter?.find { it.dokumentInfoId == document.second }
            ?: throw RuntimeException("Document not found in SAF")

        val hasAccessToArkivVariant = dokumentInfo.dokumentvarianter.any {
            it.variantformat == Variantformat.ARKIV && it.saksbehandlerHarTilgang
        }

        val hasAccessToSladdetVariant = dokumentInfo.dokumentvarianter.any {
            it.variantformat == Variantformat.SLADDET && it.saksbehandlerHarTilgang
        }

        return when {
            preferArkivvariantIfAccess && hasAccessToArkivVariant -> {
                Variantformat.ARKIV.name
            }

            hasAccessToSladdetVariant -> {
                Variantformat.SLADDET.name
            }

            hasAccessToArkivVariant -> {
                Variantformat.ARKIV.name
            }

            else -> {
                throw RuntimeException("No access to document with dokumentInfoId ${document.second} in journalpost ${document.first}")
            }
        }
    }

    fun mergePDFFiles(
        resourcesToMerge: List<Resource>,
        title: String = "merged document"
    ): Pair<FileSystemResource, String> {
        val merger = PDFMergerUtility()

        val pdDocumentInformation = PDDocumentInformation()
        pdDocumentInformation.title = title
        merger.destinationDocumentInformation = pdDocumentInformation

        val mergedDocumentFile = createFileNotYetOnDisk()

        merger.destinationFileName = mergedDocumentFile.absolutePath

        resourcesToMerge.forEach { resource ->
            if (resource is FileSystemResource) {
                merger.addSource(resource.file)
            } else {
                merger.addSource(RandomAccessReadBuffer(resource.inputStream.readBytes()))
            }
        }

        //just under 256 MB before using file system
        merger.mergeDocuments(getMixedMemorySettingsForPDFBox(250_000_000))

        //clean tmp files
        try {
            resourcesToMerge.forEach { resource ->
                if (resource is FileSystemResource) {
                    resource.file.delete()
                }
            }
        } catch (e: Exception) {
            logger.warn("couldn't delete tmp files", e)
        }

        return FileSystemResource(mergedDocumentFile) to title
    }

    fun getMergedDocument(id: UUID) = mergedDocumentRepository.getReferenceById(id)

    fun updateDocumentTitle(
        journalpostId: String,
        dokumentInfoId: String,
        title: String
    ) {

        dokarkivClient.updateDocumentTitlesOnBehalfOf(
            journalpostId = journalpostId,
            input = UpdateDocumentTitlesJournalpostInput(
                dokumenter = listOf(
                    UpdateDocumentTitleDokumentInput(
                        dokumentInfoId = dokumentInfoId,
                        tittel = title
                    )
                )
            )
        )

        val journalpost = safFacade.getJournalpostAsSaksbehandler(
            journalpostId = journalpostId,
        )

        val foedselsnummer = pdlFacade.getFoedselsnummerFromIdent(journalpost.bruker.id)

        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()

        publishInternalEvent(
            data = jacksonObjectMapper.writeValueAsString(
                JournalfoertDocumentModified(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    tittel = title,
                    traceId = currentTraceId(),
                )
            ),
            identifikator = foedselsnummer,
            type = InternalEventType.JOURNALFOERT_DOCUMENT_MODIFIED,
        )
    }

    fun addLogiskVedlegg(dokumentInfoId: String, title: String): LogiskVedleggResponse {
        val logiskVedlegg = dokarkivClient.addLogiskVedleggOnBehalfOf(
            dokumentInfoId = dokumentInfoId,
            title = title,
        )

        return LogiskVedleggResponse(
            tittel = title,
            logiskVedleggId = logiskVedlegg.logiskVedleggId
        )
    }

    fun updateLogiskVedlegg(dokumentInfoId: String, logiskVedleggId: String, title: String): LogiskVedleggResponse {
        dokarkivClient.updateLogiskVedleggOnBehalfOf(
            dokumentInfoId = dokumentInfoId,
            logiskVedleggId = logiskVedleggId,
            title = title
        )

        return LogiskVedleggResponse(
            tittel = title,
            logiskVedleggId = logiskVedleggId
        )
    }

    fun deleteLogiskVedlegg(dokumentInfoId: String, logiskVedleggId: String) {
        dokarkivClient.deleteLogiskVedleggOnBehalfOf(
            dokumentInfoId = dokumentInfoId,
            logiskVedleggId = logiskVedleggId,
        )
    }

    private fun publishInternalEvent(data: String, identifikator: String, type: InternalEventType) {
        kafkaInternalEventService.publishInternalIdentityEvent(
            InternalIdentityEvent(
                identifikator = identifikator,
                type = type,
                data = data,
            )
        )
    }

}
