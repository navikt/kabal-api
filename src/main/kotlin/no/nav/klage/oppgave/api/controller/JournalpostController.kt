package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.oppgave.api.view.JournalfoertDokumentMetadata
import no.nav.klage.oppgave.api.view.MergedDocumentsMetadata
import no.nav.klage.oppgave.api.view.ReferenceToMergedDocumentsResponse
import no.nav.klage.oppgave.api.view.UpdateDocumentTitleView
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.DokumentService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/journalposter")
class JournalpostController(
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val dokumentService: DokumentService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Operation(
        summary = "Oppdaterer filnavn i dokumentarkivet",
        description = "Oppdaterer filnavn i dokumentarkivet"
    )
    @PutMapping("/{journalpostId}/dokumenter/{dokumentInfoId}/tittel")
    fun updateTitle(
        @Parameter(description = "Id til journalpost")
        @PathVariable journalpostId: String,
        @Parameter(description = "Id til dokumentInfo")
        @PathVariable dokumentInfoId: String,
        @Parameter(description = "Ny tittel til dokumentet")
        @RequestBody input: UpdateDocumentTitleView
    ): UpdateDocumentTitleView {
        logMethodDetails(
            methodName = ::updateTitle.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        dokumentService.updateDocumentTitle(
            journalpostId = journalpostId,
            dokumentInfoId = dokumentInfoId,
            title = input.tittel
        )

        return input
    }

    @Operation(
        summary = "Henter fil fra dokumentarkivet",
        description = "Henter fil fra dokumentarkivet som pdf gitt at saksbehandler har tilgang"
    )
    @ResponseBody
    @GetMapping("/{journalpostId}/dokumenter/{dokumentInfoId}/pdf")
    fun getArkivertDokumentPDF(
        @Parameter(description = "Id til journalpost")
        @PathVariable journalpostId: String,
        @Parameter(description = "Id til dokumentInfo")
        @PathVariable dokumentInfoId: String

    ): ResponseEntity<ByteArray> {
        logMethodDetails(
            methodName = ::getArkivertDokumentPDF.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        val fysiskDokument = dokumentService.getFysiskDokument(
            journalpostId = journalpostId,
            dokumentInfoId = dokumentInfoId
        )

        val responseHeaders = HttpHeaders()
        responseHeaders.contentType = fysiskDokument.contentType
        responseHeaders.add(
            "Content-Disposition",
            "inline; filename=\"${fysiskDokument.title.removeSuffix(".pdf")}.pdf\""
        )
        return ResponseEntity(
            dokumentService.changeTitleInPDF(fysiskDokument.content, fysiskDokument.title),
            responseHeaders,
            HttpStatus.OK
        )
    }

    @Operation(
        summary = "Henter noe metadata fra dokumentarkivet",
        description = "Henter noe metadata fra dokumentarkivet gitt at saksbehandler har tilgang"
    )
    @GetMapping("/{journalpostId}/dokumenter/{dokumentInfoId}", "/{journalpostId}/dokumenter/{dokumentInfoId}/title")
    fun getArkivertDokumentMetadata(
        @Parameter(description = "Id til journalpost")
        @PathVariable journalpostId: String,
        @Parameter(description = "Id til dokumentInfo")
        @PathVariable dokumentInfoId: String
    ): JournalfoertDokumentMetadata {
        logMethodDetails(
            methodName = ::getArkivertDokumentMetadata.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )
        return dokumentService.getJournalfoertDokumentMetadata(
            journalpostId = journalpostId,
            dokumentInfoId = dokumentInfoId,
        )
    }

    @PostMapping("/mergedocuments")
    fun setDocumentsToMerge(
        @RequestBody documents: List<JournalfoertDokumentReference>
    ): ReferenceToMergedDocumentsResponse {
        logMethodDetails(
            methodName = ::setDocumentsToMerge.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        val mergedDocument = dokumentService.storeDocumentsForMerging(documents)
        return ReferenceToMergedDocumentsResponse(
            reference = mergedDocument.id,
            title = mergedDocument.title,
        )
    }

    @GetMapping("/mergedocuments/{referenceId}/pdf")
    fun getMergedDocuments(
        @PathVariable referenceId: UUID
    ): ResponseEntity<Resource> {
        logMethodDetails(
            methodName = ::getMergedDocuments.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        val (pathToMergedDocument, title) = dokumentService.mergeJournalfoerteDocuments(referenceId)
        val responseHeaders = HttpHeaders()
        responseHeaders.contentType = MediaType.APPLICATION_PDF
        responseHeaders.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$title.pdf\"")

        return ResponseEntity.ok()
            .headers(responseHeaders)
            .contentLength(pathToMergedDocument.toFile().length())
            .body(
                object : FileSystemResource(pathToMergedDocument) {
                    override fun getInputStream(): InputStream {
                        return object : FileInputStream(pathToMergedDocument.toFile()) {
                            override fun close() {
                                super.close()
                                //Override to do this after client has downloaded file
                                Files.delete(file.toPath())
                            }
                        }
                    }
                })
    }

    @GetMapping("/mergedocuments/{referenceId}", "/mergedocuments/{referenceId}/title")
    fun getMergedDocumentsMetadata(
        @PathVariable referenceId: UUID
    ): MergedDocumentsMetadata {
        logMethodDetails(
            methodName = ::getMergedDocumentsMetadata.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        val mergedDocument = dokumentService.getMergedDocument(referenceId)
        return MergedDocumentsMetadata(
            mergedDocumentId = mergedDocument.id,
            title = mergedDocument.title,
            archivedDocuments = mergedDocument.documentsToMerge.map {
                MergedDocumentsMetadata.JournalfoertDokument(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId,
                )
            }
        )
    }
}