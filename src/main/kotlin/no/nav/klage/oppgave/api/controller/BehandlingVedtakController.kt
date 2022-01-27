package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.exceptions.JournalpostNotFoundException
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.VedtakService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
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
class BehandlingVedtakController(
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val behandlingMapper: BehandlingMapper,
    private val vedtakService: VedtakService,
    private val behandlingService: BehandlingService,
    private val kabalDocumentGateway: KabalDocumentGateway
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/{id}/resultat/vedlegg")
    fun postVedlegg(
        @PathVariable("id") behandlingId: UUID,
        @ModelAttribute input: VedtakVedleggInput
    ): VedleggEditedView? {
        logBehandlingMethodDetails(
            ::postVedlegg.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        return behandlingMapper.mapToVedleggEditedView(
            vedtakService.knyttVedtaksFilTilVedtak(
                behandlingId,
                input,
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            )
        )
    }

    @DeleteMapping("/{id}/resultat/vedlegg")
    fun deleteVedlegg(
        @PathVariable("id") behandlingId: UUID
    ): VedleggEditedView {
        logBehandlingMethodDetails(
            ::deleteVedlegg.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        return behandlingMapper.mapToVedleggEditedView(
            vedtakService.slettFilTilknyttetVedtak(
                behandlingId,
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            )
        )
    }

    @ResponseBody
    @GetMapping("/{id}/resultat/pdf")
    fun getVedlegg(
        @PathVariable("id") behandlingId: UUID
    ): ResponseEntity<ByteArray> {
        logBehandlingMethodDetails(
            ::getVedlegg.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val behandling = behandlingService.getBehandling(behandlingId)
        val vedtak = vedtakService.getVedtak(behandling)

        val arkivertDokumentWithTitle =
            if (vedtak.dokumentEnhetId != null && kabalDocumentGateway.isHovedDokumentUploaded(vedtak.dokumentEnhetId!!)) {
                kabalDocumentGateway.getHovedDokumentOgMetadata(vedtak.dokumentEnhetId!!)
            } else {
                throw JournalpostNotFoundException("Vedtak er ikke lastet opp")
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
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: VedtakUtfallInput
    ): VedtakEditedView {
        logBehandlingMethodDetails(
            ::setUtfall.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        return VedtakEditedView(
            vedtakService.setUtfall(
                behandlingId,
                input.utfall?.let { Utfall.of(it) },
                innloggetSaksbehandlerRepository.getInnloggetIdent()
            ).modified
        )
    }

    @PutMapping("/{id}/resultat/hjemler")
    fun setHjemler(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: VedtakHjemlerInput
    ): VedtakEditedView {
        logBehandlingMethodDetails(
            ::setHjemler.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        return VedtakEditedView(
            vedtakService.setHjemler(
                behandlingId = behandlingId,
                hjemler = input.hjemler?.map { Registreringshjemmel.of(it) }?.toSet() ?: emptySet(),
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
            ).modified
        )
    }

    @PutMapping("/{id}/smarteditorid")
    fun setSmartEditorId(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: SmartEditorIdInput
    ): VedtakEditedView {
        logBehandlingMethodDetails(
            ::setSmartEditorId.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        return VedtakEditedView(
            modified = vedtakService.setSmartEditorId(
                behandlingId = behandlingId,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent(),
                smartEditorId = input.smartEditorId
            ).modified
        )
    }

    @GetMapping("/{id}/smarteditorid")
    fun getSmartEditorId(
        @PathVariable("id") behandlingId: UUID
    ): SmartEditorIdView {
        logBehandlingMethodDetails(
            ::getSmartEditorId.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        return SmartEditorIdView(
            smartEditorId = behandlingService.getBehandling(behandlingId)
                .currentDelbehandling().smartEditorId
        )
    }

    @DeleteMapping("/{id}/smarteditorid")
    fun deleteSmartEditorId(
        @PathVariable("id") behandlingId: UUID
    ): VedtakEditedView {
        logBehandlingMethodDetails(
            ::deleteSmartEditorId.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        return VedtakEditedView(
            modified = vedtakService.deleteSmartEditorId(
                behandlingId = behandlingId,
                utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
            ).modified
        )
    }

}
