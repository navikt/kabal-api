package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.view.BehandlingEditedView
import no.nav.klage.oppgave.api.view.DokumenterResponse
import no.nav.klage.oppgave.api.view.TilknyttetDokument
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.DokumentService
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
class BehandlingDokumentController(
    private val behandlingService: BehandlingService,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val dokumentService: DokumentService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @ApiOperation(
        value = "Hent metadata om dokumenter for brukeren som saken gjelder"
    )
    @GetMapping("/{id}/arkivertedokumenter", produces = ["application/json"])
    fun fetchDokumenter(
        @ApiParam(value = "Id til klagebehandlingen i vårt system")
        @PathVariable("id") behandlingId: UUID,
        @RequestParam(required = false, name = "antall", defaultValue = "10") pageSize: Int,
        @RequestParam(required = false, name = "forrigeSide") previousPageRef: String? = null,
        @RequestParam(required = false, name = "temaer") temaer: List<String>? = emptyList()
    ): DokumenterResponse {
        return behandlingService.fetchDokumentlisteForBehandling(
            behandlingId = behandlingId,
            temaer = temaer?.map { Tema.of(it) } ?: emptyList(),
            pageSize = pageSize,
            previousPageRef = previousPageRef
        )
    }

    @ApiOperation(
        value = "Henter fil fra dokumentarkivet",
        notes = "Henter fil fra dokumentarkivet som pdf gitt at saksbehandler har tilgang"
    )
    @ResponseBody
    @GetMapping("/{id}/arkivertedokumenter/{journalpostId}/{dokumentInfoId}/pdf")
    fun getArkivertDokument(
        @ApiParam(value = "Id til behandlingen i vårt system")
        @PathVariable("id") behandlingId: UUID,
        @ApiParam(value = "Id til journalpost")
        @PathVariable journalpostId: String,
        @ApiParam(value = "Id til dokumentInfo")
        @PathVariable dokumentInfoId: String

    ): ResponseEntity<ByteArray> {
        logger.debug(
            "Get getArkivertDokument is requested. behandlingsid: {} - journalpostId: {} - dokumentInfoId: {}",
            behandlingId,
            journalpostId,
            dokumentInfoId
        )

        val arkivertDokument = dokumentService.getArkivertDokument(journalpostId, dokumentInfoId)

        val responseHeaders = HttpHeaders()
        responseHeaders.contentType = arkivertDokument.contentType
        responseHeaders.add("Content-Disposition", "inline")
        return ResponseEntity(
            arkivertDokument.bytes,
            responseHeaders,
            HttpStatus.OK
        )
    }

    @ApiOperation(
        value = "Henter metadata om dokumenter knyttet til en behandling",
        notes = "Henter metadata om dokumenter knyttet til en behandling. Berikes med data fra SAF."
    )
    @GetMapping("/{id}/dokumenttilknytninger", produces = ["application/json"])
    fun fetchConnectedDokumenter(
        @ApiParam(value = "Id til behandlingen i vårt system")
        @PathVariable("id") behandlingId: UUID
    ): DokumenterResponse {
        return behandlingService.fetchJournalposterConnectedToBehandling(behandlingId)
    }

    @PostMapping("/{id}/dokumenttilknytninger")
    fun setTilknyttetDokument(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: TilknyttetDokument
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setTilknyttetDokument.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val modified = behandlingService.connectDokumentToBehandling(
            behandlingId = behandlingId,
            journalpostId = input.journalpostId,
            dokumentInfoId = input.dokumentInfoId,
            saksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return BehandlingEditedView(modified = modified)
    }

    @DeleteMapping("/{id}/dokumenttilknytninger/{journalpostId}/{dokumentInfoId}")
    fun removeTilknyttetDokument(
        @PathVariable("id") behandlingId: UUID,
        @PathVariable("journalpostId") journalpostId: String,
        @PathVariable("dokumentInfoId") dokumentInfoId: String
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::removeTilknyttetDokument.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val modified = behandlingService.disconnectDokumentFromBehandling(
            behandlingId = behandlingId,
            journalpostId = journalpostId,
            dokumentInfoId = dokumentInfoId,
            saksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return BehandlingEditedView(modified)
    }
}