package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import no.nav.klage.oppgave.api.mapper.KlagebehandlingMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.kodeverk.Hjemmel
import no.nav.klage.oppgave.domain.kodeverk.Utfall
import no.nav.klage.oppgave.exceptions.JournalpostNotFoundException
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.DokumentService
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
    private val dokumentService: DokumentService,
    private val fileApiService: FileApiService
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
                klagebehandlingService.getKlagebehandlingForUpdate(klagebehandlingId),
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
                klagebehandlingService.getKlagebehandlingForUpdate(klagebehandlingId),
                input.hjemler?.map { Hjemmel.of(it) }?.toSet() ?: emptySet(),
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            ).modified
        )
    }
}
