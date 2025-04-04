package no.nav.klage.innsyn.service

import no.nav.klage.dokument.service.DokumentUnderArbeidCommonService
import no.nav.klage.innsyn.api.view.SakView
import no.nav.klage.innsyn.client.safselvbetjening.SafSelvbetjeningGraphQlClient
import no.nav.klage.innsyn.client.safselvbetjening.SafSelvbetjeningRestClient
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.klage.Omgjoeringskravbehandling
import no.nav.klage.oppgave.util.getLogger
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.io.RandomAccessStreamCache
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocumentInformation
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@Service
class DocumentService(
    private val safSelvbetjeningGraphQlClient: SafSelvbetjeningGraphQlClient,
    private val safSelvbetjeningRestClient: SafSelvbetjeningRestClient,
    private val dokumentUnderArbeidCommonService: DokumentUnderArbeidCommonService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getJournalpostPdf(journalpostId: String): Pair<Path, String> {
        val journalpostInfo = safSelvbetjeningGraphQlClient.getJournalpostById(journalpostId = journalpostId)

        if (journalpostInfo.data?.journalpostById?.dokumenter.isNullOrEmpty()) {
            throw Exception("Fikk ikke hentet fil fra arkivet.")
        }

        val journalpostIdAndDokumentInfoIdList = journalpostInfo.data!!.journalpostById!!.dokumenter.map {
            journalpostId to it.dokumentInfoId
        }

        return mergeJournalfoerteDocuments(
            documentsToMerge = journalpostIdAndDokumentInfoIdList,
            title = journalpostInfo.data.journalpostById!!.tittel
        )
    }

    private fun mergeJournalfoerteDocuments(
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
            safSelvbetjeningRestClient.downloadDocumentAsMono(
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

    @Throws(IOException::class)
    private fun getMixedMemorySettingsForPDFBox(bytes: Long): RandomAccessStreamCache.StreamCacheCreateFunction {
        return MemoryUsageSetting.setupMixed(bytes).streamCache
    }

    fun getSvarbrev(behandling: Behandling): SakView.Event.EventDocument? {
        val svarbrev = dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
            behandling.id
        ).filter {
            it.dokumentType in listOf(
                DokumentType.SVARBREV,
            )
        }.sortedBy { it.ferdigstilt }.firstOrNull()

        if (svarbrev == null) return null

        val journalpostId = if (svarbrev.brevmottakere.size == 1) {
            if (svarbrev.brevmottakere.first().identifikator == behandling.sakenGjelder.partId.value) {
                svarbrev.dokarkivReferences.first().journalpostId
            } else null
        } else {
            if (svarbrev.brevmottakere.any { it.identifikator == behandling.sakenGjelder.partId.value }) {
                val journalpostIdList = svarbrev.dokarkivReferences.map { it.journalpostId }
                var accessibleJournalpostId: String? = null
                journalpostIdList.forEach { journalpostId ->
                    val journalpostInfo =
                        safSelvbetjeningGraphQlClient.getJournalpostById(journalpostId = journalpostId)
                    if (journalpostInfo.errors.isNullOrEmpty()) {
                        accessibleJournalpostId = journalpostInfo.data?.journalpostById?.journalpostId
                    }
                }
                accessibleJournalpostId
            } else null
        }

        return SakView.Event.EventDocument(
            title = svarbrev.name,
            archiveDate = svarbrev.ferdigstilt!!.toLocalDate(),
            journalpostId = journalpostId,
            eventDocumentType = when (behandling) {
                is Klagebehandling, is Ankebehandling, is Omgjoeringskravbehandling -> SakView.Event.EventDocument.EventDocumentType.SVARBREV
                else -> throw RuntimeException(
                    "Wrong behandling type"
                )
            }
        )
    }
}