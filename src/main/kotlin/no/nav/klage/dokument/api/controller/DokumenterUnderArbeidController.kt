package no.nav.klage.dokument.api.controller


import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.dokument.api.mapper.DokumentInputMapper
import no.nav.klage.dokument.api.mapper.DokumentMapper
import no.nav.klage.dokument.api.view.*
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.oppgave.api.view.DokumentUnderArbeidMetadata
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.util.*

@RestController
@Tag(name = "kabal-api-dokumenter")
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/behandlinger/{behandlingId}/dokumenter")
class DokumentUnderArbeidController(
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val dokumentMapper: DokumentMapper,
    private val dokumentInputMapper: DokumentInputMapper,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping
    fun findDokumenter(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): List<DokumentView> {
        return dokumentUnderArbeidService.getDokumenterUnderArbeidViewList(behandlingId = behandlingId)
    }

    @PostMapping("/fil")
    fun createAndUploadDokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @ModelAttribute input: FilInput
    ): DokumentView {
        logger.debug("Kall mottatt på createAndUploadDokument")
        val opplastetFil = dokumentInputMapper.mapToMellomlagretDokument(
            multipartFile = input.file,
            tittel = input.file.originalFilename,
            dokumentType = DokumentType.of(input.dokumentTypeId),
        )
        return dokumentUnderArbeidService.createOpplastetDokumentUnderArbeid(
            behandlingId = behandlingId,
            dokumentType = DokumentType.of(input.dokumentTypeId),
            opplastetFil = opplastetFil,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            tittel = opplastetFil.title,
            parentId = input.parentId,
        )
    }

    @PostMapping("/journalfoertedokumenter")
    fun addJournalfoerteDokumenterAsVedlegg(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: JournalfoerteDokumenterInput
    ): JournalfoerteDokumenterResponse {
        logger.debug("Kall mottatt på addJournalfoerteDokumenterAsVedlegg")
        return dokumentUnderArbeidService.addJournalfoerteDokumenterAsVedlegg(
            behandlingId = behandlingId,
            journalfoerteDokumenterInput = input,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
        )
    }

    @PutMapping("/{dokumentId}/dokumenttype")
    fun endreDokumentType(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
        @RequestBody input: DokumentTypeInput
    ): DocumentModified {
        return DocumentModified(
            modified = dokumentUnderArbeidService.updateDokumentType(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                newDokumentType = DokumentType.of(input.dokumentTypeId),
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            ).modified
        )
    }

    @PutMapping("/{dokumentId}/datomottatt")
    fun setDatoMottatt(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
        @RequestBody input: DatoMottattInput
    ): DocumentModified {
        return DocumentModified(
            modified = dokumentUnderArbeidService.updateDatoMottatt(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                datoMottatt = input.datoMottatt,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            ).modified
        )
    }

    @PutMapping("/{dokumentId}/inngaaendekanal")
    fun setInngaaendeKanal(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
        @RequestBody input: InngaaendeKanalInput
    ): DocumentModified {
        return DocumentModified(
            modified = dokumentUnderArbeidService.updateInngaaendeKanal(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                inngaaendeKanal = input.kanal,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            ).modified
        )
    }

    @PutMapping("/{dokumentId}/avsender")
    fun setAvsender(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
        @RequestBody input: AvsenderInput
    ): DocumentModified {
        return DocumentModified(
            modified = dokumentUnderArbeidService.updateAvsender(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                avsenderInput = input,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            ).modified
        )
    }

    @PutMapping("/{dokumentId}/mottakere")
    fun setMottakere(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
        @RequestBody input: MottakerInput
    ): DocumentModified {
        return DocumentModified(
            modified = dokumentUnderArbeidService.updateMottakere(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                mottakerInput = input,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            ).modified
        )
    }

    @ResponseBody
    @GetMapping("/{dokumentId}/pdf")
    fun getPdf(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
    ): ResponseEntity<ByteArray> {
        logger.debug("Kall mottatt på getPdf for {}", dokumentId)
        return dokumentMapper.mapToByteArray(
            dokumentUnderArbeidService.getFysiskDokument(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        )
    }

    @GetMapping("/{dokumentId}/title")
    fun getTitle(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
    ): DokumentUnderArbeidMetadata {
        logger.debug("Kall mottatt på getMetadata for {}", dokumentId)

        return DokumentUnderArbeidMetadata(
            behandlingId = behandlingId,
            documentId = dokumentId,
            title = dokumentUnderArbeidService.getDokumentUnderArbeid(dokumentId).name
        )
    }

    @GetMapping("/{dokumentId}")
    fun getDokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
    ): DokumentView {
        logger.debug("Kall mottatt på getDokument for {}", dokumentId)

        return dokumentUnderArbeidService.getDokumentUnderArbeidView(
            dokumentUnderArbeidId = dokumentId,
            behandlingId = behandlingId
        )
    }

    @GetMapping("/{dokumentId}/vedleggsoversikt")
    fun getMetadataForInnholdsfortegnelse(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
    ): DokumentUnderArbeidMetadata {
        logger.debug("Kall mottatt på getMetadataForInnholdsfortegnelse for {}", dokumentId)

        return DokumentUnderArbeidMetadata(
            behandlingId = behandlingId,
            documentId = dokumentId,
            title = "Vedleggsoversikt"
        )
    }

    @GetMapping("/{hoveddokumentId}/vedleggsoversikt/pdf")
    @ResponseBody
    fun getInnholdsfortegnelsePdf(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("hoveddokumentId") hoveddokumentId: UUID,
    ): ResponseEntity<ByteArray> {
        logger.debug("Kall mottatt på getInnholdsfortegnelsePdf for {}", hoveddokumentId)
        return dokumentMapper.mapToByteArray(
            dokumentUnderArbeidService.getInnholdsfortegnelseAsFysiskDokument(
                behandlingId = behandlingId,
                hoveddokumentId = hoveddokumentId,
                innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
            )
        )
    }

    @DeleteMapping("/{dokumentId}")
    fun deleteDokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") dokumentId: UUID,
    ) {
        logger.debug("Kall mottatt på deleteDokument for {}", dokumentId)
        dokumentUnderArbeidService.slettDokument(
            dokumentId = dokumentId,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )
    }

    @PutMapping("/{dokumentId}/parent")
    fun kobleEllerFrikobleVedlegg(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentId") persistentDokumentId: UUID,
        @RequestBody input: OptionalPersistentDokumentIdInput
    ): DokumentViewWithList {
        logger.debug("Kall mottatt på kobleEllerFrikobleVedlegg for {}", persistentDokumentId)
        try {
            return dokumentUnderArbeidService.kobleEllerFrikobleVedlegg(
                behandlingId = behandlingId,
                persistentDokumentId = persistentDokumentId,
                optionalParentInput = input
            )
        } catch (e: Exception) {
            logger.error("Feilet under kobling av dokument $persistentDokumentId med ${input.dokumentId}", e)
            throw e
        }
    }

    @PostMapping("/{dokumentid}/ferdigstill")
    fun idempotentOpprettOgFerdigstillDokumentEnhetFraHovedDokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentid") dokumentId: UUID,
    ): DocumentModified {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()

        return DocumentModified(
            modified = dokumentUnderArbeidService.finnOgMarkerFerdigHovedDokument(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                innloggetIdent = ident,
            ).modified
        )
    }

    @GetMapping("/{dokumentid}/validate")
    fun validateDokument(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentid") dokumentId: UUID,
    ): List<DocumentValidationResponse> {
        //TODO only called for hoveddokumenter?
        return dokumentUnderArbeidService.validatePlaceholdersInSmartDocumentAndVedlegg(dokumentId)
    }

    @PutMapping("/{dokumentid}/tittel")
    fun changeDocumentTitle(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("dokumentid") dokumentId: UUID,
        @RequestBody input: DokumentTitleInput,
    ): DocumentModified {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        return DocumentModified(
            modified = dokumentUnderArbeidService.updateDokumentTitle(
                behandlingId = behandlingId,
                dokumentId = dokumentId,
                dokumentTitle = input.title,
                innloggetIdent = ident,
            ).modified
        )
    }

    @GetMapping("/mergedocuments/{dokumentUnderArbeidId}/pdf")
    fun getMergedDocuments(
        @PathVariable dokumentUnderArbeidId: UUID
    ): ResponseEntity<Resource> {
        logMethodDetails(
            methodName = ::getMergedDocuments.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        val (pathToMergedDocument, title) = dokumentUnderArbeidService.mergeDUAAndCreatePDF(dokumentUnderArbeidId)
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
}