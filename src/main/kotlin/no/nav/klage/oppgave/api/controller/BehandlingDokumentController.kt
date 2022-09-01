package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "kabal-api")
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

    @Operation(
        summary = "Hent metadata om dokumenter for brukeren som saken gjelder"
    )
    @GetMapping("/{id}/arkivertedokumenter", produces = ["application/json"])
    fun fetchDokumenter(
        @Parameter(description = "Id til klagebehandlingen i vårt system")
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

    @Operation(
        summary = "Henter fil fra dokumentarkivet",
        description = "Henter fil fra dokumentarkivet som pdf gitt at saksbehandler har tilgang"
    )
    @ResponseBody
    @GetMapping("/{id}/arkivertedokumenter/{journalpostId}/{dokumentInfoId}/pdf")
    fun getArkivertDokument(
        @Parameter(description = "Id til behandlingen i vårt system")
        @PathVariable("id") behandlingId: UUID,
        @Parameter(description = "Id til journalpost")
        @PathVariable journalpostId: String,
        @Parameter(description = "Id til dokumentInfo")
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

    @Operation(
        summary = "Henter metadata om dokumenter knyttet til en behandling",
        description = "Henter metadata om dokumenter knyttet til en behandling. Berikes med data fra SAF."
    )
    @GetMapping("/{id}/dokumenttilknytninger", produces = ["application/json"])
    fun fetchConnectedDokumenter(
        @Parameter(description = "Id til behandlingen i vårt system")
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