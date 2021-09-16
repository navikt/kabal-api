package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import no.nav.klage.oppgave.api.mapper.KlagebehandlingMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.exceptions.BehandlingsidWrongFormatException
import no.nav.klage.oppgave.exceptions.JournalpostNotFoundException
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.DokumentService
import no.nav.klage.oppgave.service.FileApiService
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.service.VedtakService
import no.nav.klage.oppgave.util.getLogger
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
    private val dokumentService: DokumentService,
    private val fileApiService: FileApiService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/{klagebehandlingid}/vedtak/{vedtakid}/vedlegg")
    fun postVedlegg(
        @PathVariable("klagebehandlingid") klagebehandlingId: String,
        @PathVariable("vedtakid") vedtakId: String,
        @ModelAttribute input: VedtakVedleggInput
    ): VedleggEditedView? {
        logMethodDetails("postVedlegg", klagebehandlingId)

        return klagebehandlingMapper.mapToVedleggEditedView(
            vedtakService.knyttVedtaksFilTilVedtak(
                klagebehandlingId.toUUIDOrException(),
                input,
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            )
        )
    }

    @DeleteMapping("/{klagebehandlingid}/vedtak/{vedtakid}/vedlegg")
    fun deleteVedlegg(
        @PathVariable("klagebehandlingid") klagebehandlingId: String,
        @PathVariable("vedtakid") vedtakId: String,
        @RequestBody input: VedtakSlettVedleggInput
    ): VedleggEditedView {
        logMethodDetails("deleteVedlegg", klagebehandlingId)

        return klagebehandlingMapper.mapToVedleggEditedView(
            vedtakService.slettFilTilknyttetVedtak(
                klagebehandlingId.toUUIDOrException(),
                input,
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            )
        )
    }

    @PostMapping("/{klagebehandlingid}/vedtak/{vedtakid}/fullfoer")
    fun fullfoerVedtak(
        @PathVariable("klagebehandlingid") klagebehandlingId: String,
        @PathVariable("vedtakid") vedtakId: String,
        @RequestBody input: VedtakFullfoerInput
    ): VedtakFullfoertView {
        logMethodDetails("fullfoerVedtak", klagebehandlingId)
        val klagebehandling = vedtakService.ferdigstillVedtak(
            klagebehandlingId.toUUIDOrException(),
            input,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return klagebehandlingMapper.mapToVedtakFullfoertView(klagebehandling)
    }

    @ResponseBody
    @GetMapping("/{klagebehandlingid}/vedtak/{vedtakid}/pdf")
    fun getVedlegg(
        @PathVariable("klagebehandlingid") klagebehandlingId: String,
        @PathVariable("vedtakid") vedtakId: String,
    ): ResponseEntity<ByteArray> {
        logMethodDetails("getVedlegg", klagebehandlingId)
        val klagebehandling = klagebehandlingService.getKlagebehandling(klagebehandlingId.toUUIDOrException())
        val vedtak = vedtakService.getVedtak(klagebehandling)

        val arkivertDokumentWithTitle =
            when {
                vedtak.journalpostId != null -> {
                    dokumentService.getArkivertDokumentWithTitleAsSaksbehandler(vedtak.journalpostId!!)
                }
                vedtak.mellomlagerId != null -> {
                    fileApiService.getUploadedDocument(vedtak.mellomlagerId!!)
                }
                else -> {
                    throw JournalpostNotFoundException("Vedtak med id $vedtakId er ikke lastet opp")
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

    private fun String.toUUIDOrException() =
        try {
            UUID.fromString(this)
        } catch (e: Exception) {
            logger.error("Input could not be parsed as an UUID", e)
            throw BehandlingsidWrongFormatException("Input could not be parsed as an UUID")
        }

    private fun logMethodDetails(methodName: String, klagebehandlingId: String) {
        logger.debug(
            "{} is requested by ident {} for klagebehandlingId {}",
            methodName,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId
        )
    }
}
