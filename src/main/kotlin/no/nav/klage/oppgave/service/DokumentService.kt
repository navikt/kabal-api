package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.ObjectMapper
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
import no.nav.klage.oppgave.clients.saf.graphql.*
import no.nav.klage.oppgave.clients.saf.rest.SafRestClient
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.DocumentToMerge
import no.nav.klage.oppgave.domain.klage.MergedDocument
import no.nav.klage.oppgave.domain.klage.Saksdokument
import no.nav.klage.oppgave.exceptions.JournalpostNotFoundException
import no.nav.klage.oppgave.repositories.MergedDocumentRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.io.RandomAccessStreamCache.StreamCacheCreateFunction
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Flux
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
    private val dokarkivClient: DokarkivClient
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        private val objectMapper: ObjectMapper = ourJacksonObjectMapper()
    }

    fun fetchDokumentlisteForBehandling(
        behandling: Behandling,
        temaer: List<Tema>,
        pageSize: Int,
        previousPageRef: String?
    ): DokumenterResponse {
        if (behandling.sakenGjelder.erPerson()) {
            val dokumentoversiktBruker: DokumentoversiktBruker =
                safFacade.getDokumentoversiktBrukerAsSaksbehandler(
                    behandling.sakenGjelder.partId.value,
                    mapTema(temaer),
                    pageSize,
                    previousPageRef
                )

            //Attempt to track down elusive bug with repeated documents
            val uniqueJournalposter = dokumentoversiktBruker.journalposter.map { it.journalpostId }.toSet()
            if (uniqueJournalposter.size != dokumentoversiktBruker.journalposter.size) {
                secureLogger.error(
                    "Received list of non unique documents from SAF.\nUnique list: ${
                        uniqueJournalposter.joinToString()
                    }. " + "\nFull list: ${
                        dokumentoversiktBruker.journalposter.joinToString { it.journalpostId }
                    }" + "\nParams: fnr: ${behandling.sakenGjelder.partId.value}, behandlingId: ${behandling.id}, " +
                            "temaer: ${temaer.joinToString()}, pageSize: $pageSize, previousPageRef: $previousPageRef"
                )
            }

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
                temaIdList = dokumentReferanseList.mapNotNull { it.temaId }.toSet().toList(),
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

    fun getFysiskDokument(journalpostId: String, dokumentInfoId: String): FysiskDokument {
        val resource = safRestClient.getDokument(dokumentInfoId, journalpostId)

        return FysiskDokument(
            title = getDocumentTitle(journalpostId = journalpostId, dokumentInfoId = dokumentInfoId),
            content = resource,
            contentType = MediaType.APPLICATION_PDF, //for now
        )
    }

    fun getFysiskDokumentAsStream(journalpostId: String, dokumentInfoId: String): Pair<Flux<DataBuffer>, ClientResponse.Headers> {
        return safRestClient.getDokumentAsStream(dokumentInfoId, journalpostId)
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
            harTilgangTilArkivvariant = dokumentMapper.harTilgangTilArkivvariant(dokumentInfo)
        )
    }

    @Throws(IOException::class)
    private fun getMixedMemorySettingsForPDFBox(bytes: Long): StreamCacheCreateFunction {
        return MemoryUsageSetting.setupMixed(bytes).streamCache
    }

    fun changeTitleInPDF(resource: Resource, title: String): Resource {
        try {
            val tmpFile = Files.createTempFile(null, null).toFile()
            val timeMillis = measureTimeMillis {
                val memorySettingsForPDFBox: Long = 50_000_000
                val document: PDDocument = if (resource is FileSystemResource) {
                    Loader.loadPDF(resource.file, getMixedMemorySettingsForPDFBox(memorySettingsForPDFBox))
                } else {
                    Loader.loadPDF(RandomAccessReadBuffer(resource.contentAsByteArray), getMixedMemorySettingsForPDFBox(
                        memorySettingsForPDFBox
                    ))
                }

                val info: PDDocumentInformation = document.documentInformation
                info.title = title
                document.isAllSecurityToBeRemoved = true
                document.save(tmpFile)
                document.close()
            }
            secureLogger.debug("changeTitleInPDF with title $title took $timeMillis ms")

            if (resource is FileSystemResource) {
                resource.file.delete()
            }

            return FileSystemResource(tmpFile)
        } catch (e: Exception) {
            secureLogger.warn("Unable to change title for pdf content", e)
            return resource
        }
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
                }.toSet(),
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
        val truncatedMessage = " ... " + (documents.size - numberOfDocumentNamesToShow) + " til"
        val journalpostList = safFacade.getJournalposter(
            journalpostIdSet = documents.map { it.journalpostId }.toSet(),
            fnr = null,
            saksbehandlerContext = true,
        )

        return "(${documents.size}) " + documents
            .joinToString(
                limit = numberOfDocumentNamesToShow,
                truncated = truncatedMessage
            ) { journalfoertDokumentReference ->
                journalpostList
                    .find { it.journalpostId == journalfoertDokumentReference.journalpostId }!!
                    .dokumenter?.find { it.dokumentInfoId == journalfoertDokumentReference.dokumentInfoId }?.tittel
                    ?: throw RuntimeException("Document/title not found in Dokarkiv")
            }
    }

    fun mergeJournalfoerteDocuments(id: UUID): Pair<Path, String> {
        val mergedDocument = mergedDocumentRepository.getReferenceById(id)
        val documentsToMerge = mergedDocument.documentsToMerge.sortedBy { it.index }

        return mergeJournalfoerteDocuments(
            documentsToMerge.map { it.journalpostId to it.dokumentInfoId },
            mergedDocument.title
        )
    }

    fun mergeJournalfoerteDocuments(
        documentsToMerge: List<Pair<String, String>>,
        title: String = "merged document"
    ): Pair<Path, String> {
        if (documentsToMerge.isEmpty()) {
            throw RuntimeException("No documents to merge")
        }

        val merger = PDFMergerUtility()

        val pdDocumentInformation = PDDocumentInformation()
        pdDocumentInformation.title = title
        merger.destinationDocumentInformation = pdDocumentInformation

        val pathToMergedDocument = Files.createTempFile(null, null)
        pathToMergedDocument.toFile().deleteOnExit()

        merger.destinationFileName = pathToMergedDocument.toString()

        val documentsWithPaths = documentsToMerge.map {
            val tmpFile = Files.createTempFile("", "")
            it to tmpFile
        }

        Flux.fromIterable(documentsWithPaths).flatMapSequential { (document, path) ->
            safRestClient.downloadDocumentAsMono(
                journalpostId = document.first,
                dokumentInfoId = document.second,
                pathToFile = path,
            )
        }.collectList().block()

        documentsWithPaths.forEach { (_, path) ->
            merger.addSource(path.toFile())
        }

        //just under 256 MB before using file system
        merger.mergeDocuments(getMixedMemorySettingsForPDFBox(250_000_000))

        //clean tmp files that were downloaded from SAF
        try {
            documentsWithPaths.forEach { (_, pathToTmpFile) ->
                pathToTmpFile.toFile().delete()
            }
        } catch (e: Exception) {
            logger.warn("couldn't delete tmp files", e)
        }

        return pathToMergedDocument to title
    }

    fun mergePDFFiles(resourcesToMerge: List<Resource>, title: String = "merged document"): Pair<FileSystemResource, String> {
        val merger = PDFMergerUtility()

        val pdDocumentInformation = PDDocumentInformation()
        pdDocumentInformation.title = title
        merger.destinationDocumentInformation = pdDocumentInformation

        val pathToMergedDocument = Files.createTempFile(null, null)
        pathToMergedDocument.toFile().deleteOnExit()

        merger.destinationFileName = pathToMergedDocument.toString()

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

        return FileSystemResource(pathToMergedDocument) to title
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

        val foedselsnummer = pdlFacade.getFoedselsnummerFromSomeIdent(journalpost.bruker.id)

        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                JournalfoertDocumentModified(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = LocalDateTime.now(),
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId,
                    tittel = title,
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
