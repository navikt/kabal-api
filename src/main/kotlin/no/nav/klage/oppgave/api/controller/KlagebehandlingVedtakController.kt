package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import no.nav.klage.oppgave.api.mapper.KlagebehandlingMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.kodeverk.Hjemmel
import no.nav.klage.oppgave.domain.kodeverk.Utfall
import no.nav.klage.oppgave.exceptions.JournalpostNotFoundException
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.FileApiService
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.service.VedtakService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logKlagebehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/klagebehandlinger")
class KlagebehandlingVedtakController(
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val klagebehandlingMapper: KlagebehandlingMapper,
    private val vedtakService: VedtakService,
    private val klagebehandlingService: KlagebehandlingService,
    private val fileApiService: FileApiService,
    private val kabalDocumentGateway: KabalDocumentGateway
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/{klagebehandlingid}/resultat/vedlegg")
    fun postVedlegg(
        @PathVariable("klagebehandlingid") klagebehandlingId: UUID,
        @ModelAttribute input: VedtakVedleggInput
    ): VedleggEditedView? {
        logKlagebehandlingMethodDetails(
            "postVedlegg",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )
        return klagebehandlingMapper.mapToVedleggEditedView(
            vedtakService.knyttVedtaksFilTilVedtak(
                klagebehandlingId,
                input,
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            )
        )
    }

    @DeleteMapping("/{klagebehandlingid}/resultat/vedlegg")
    fun deleteVedlegg(
        @PathVariable("klagebehandlingid") klagebehandlingId: UUID
    ): VedleggEditedView {
        logKlagebehandlingMethodDetails(
            "deleteVedlegg",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )
        return klagebehandlingMapper.mapToVedleggEditedView(
            vedtakService.slettFilTilknyttetVedtak(
                klagebehandlingId,
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            )
        )
    }

    @ResponseBody
    @GetMapping("/{klagebehandlingid}/resultat/pdf")
    fun getVedlegg(
        @PathVariable("klagebehandlingid") klagebehandlingId: UUID
    ): ResponseEntity<ByteArray> {
        logKlagebehandlingMethodDetails(
            "getVedlegg",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )
        val klagebehandling = klagebehandlingService.getKlagebehandling(klagebehandlingId)
        val vedtak = vedtakService.getVedtak(klagebehandling)

        val arkivertDokumentWithTitle =
            when {
                vedtak.dokumentEnhetId != null && kabalDocumentGateway.isHovedDokumentUploaded(vedtak.dokumentEnhetId!!) -> {
                    kabalDocumentGateway.getHovedDokumentOgMetadata(vedtak.dokumentEnhetId!!)
                }
                vedtak.mellomlagerId != null -> {
                    fileApiService.getUploadedDocument(vedtak.mellomlagerId!!)
                }
                else -> {
                    throw JournalpostNotFoundException("Vedtak er ikke lastet opp")
                }
            }

        val responseHeaders = HttpHeaders()
        responseHeaders.contentType = arkivertDokumentWithTitle.contentType
        responseHeaders.add("Content-Disposition", "inline; filename=${arkivertDokumentWithTitle.title}")
        return ResponseEntity(
            arkivertDokumentWithTitle.content,
            responseHeaders,
            HttpStatus.OK
        )
    }

    @PutMapping("/{id}/resultat/utfall")
    fun setUtfall(
        @PathVariable("id") klagebehandlingId: UUID,
        @RequestBody input: VedtakUtfallInput
    ): VedtakEditedView {
        logKlagebehandlingMethodDetails(
            "setUtfall", innloggetSaksbehandlerRepository.getInnloggetIdent(), klagebehandlingId,
            logger
        )
        return VedtakEditedView(
            vedtakService.setUtfall(
                klagebehandlingId,
                input.utfall?.let { Utfall.of(it) },
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            ).modified
        )
    }

    @PutMapping("/{id}/resultat/hjemler")
    fun setHjemler(
        @PathVariable("id") klagebehandlingId: UUID,
        @RequestBody input: VedtakHjemlerInput
    ): VedtakEditedView {
        logKlagebehandlingMethodDetails(
            "setHjemler", innloggetSaksbehandlerRepository.getInnloggetIdent(), klagebehandlingId,
            logger
        )
        return VedtakEditedView(
            vedtakService.setHjemler(
                klagebehandlingId,
                input.hjemler?.map { Hjemmel.of(it) }?.toSet() ?: emptySet(),
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            ).modified
        )
    }

    @PutMapping("/{id}/smarteditorid")
    fun setSmartEditorId(
        @PathVariable("id") klagebehandlingId: UUID,
        @RequestBody input: SmartEditorIdInput
    ): VedtakEditedView {
        logKlagebehandlingMethodDetails(
            "setSmartEditorId", innloggetSaksbehandlerRepository.getInnloggetIdent(), klagebehandlingId,
            logger
        )
        return VedtakEditedView(
            modified = vedtakService.setSmartEditorId(
                klagebehandlingId = klagebehandlingId,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent(),
                smartEditorId = input.smartEditorId
            ).modified
        )
    }

    @GetMapping("/{id}/smarteditorid")
    fun getSmartEditorId(
        @PathVariable("id") klagebehandlingId: UUID
    ): SmartEditorIdView {
        logKlagebehandlingMethodDetails(
            "getSmartEditorId", innloggetSaksbehandlerRepository.getInnloggetIdent(), klagebehandlingId,
            logger
        )
        return SmartEditorIdView(
            smartEditorId = klagebehandlingService.getKlagebehandling(klagebehandlingId)
                .getVedtakOrException().smartEditorId
        )
    }

    @DeleteMapping("/{id}/smarteditorid")
    fun deleteSmartEditorId(
        @PathVariable("id") klagebehandlingId: UUID
    ): VedtakEditedView {
        logKlagebehandlingMethodDetails(
            "deleteSmartEditorId", innloggetSaksbehandlerRepository.getInnloggetIdent(), klagebehandlingId,
            logger
        )
        return VedtakEditedView(
            modified = vedtakService.deleteSmartEditorId(
                klagebehandlingId = klagebehandlingId,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
            ).modified
        )
    }

}
