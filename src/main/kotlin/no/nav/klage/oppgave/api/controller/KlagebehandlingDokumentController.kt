package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.view.DokumenterResponse
import no.nav.klage.oppgave.api.view.KlagebehandlingEditedView
import no.nav.klage.oppgave.api.view.TilknyttetDokument
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.DokumentService
import no.nav.klage.oppgave.service.KlagebehandlingService
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
class KlagebehandlingDokumentController(
    private val klagebehandlingService: KlagebehandlingService,
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
        @PathVariable("id") klagebehandlingId: UUID,
        @RequestParam(required = false, name = "antall", defaultValue = "10") pageSize: Int,
        @RequestParam(required = false, name = "forrigeSide") previousPageRef: String? = null,
        @RequestParam(required = false, name = "temaer") temaer: List<String>? = emptyList()
    ): DokumenterResponse {
        return klagebehandlingService.fetchDokumentlisteForKlagebehandling(
            klagebehandlingId = klagebehandlingId,
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
        @ApiParam(value = "Id til klagebehandlingen i vårt system")
        @PathVariable("id") klagebehandlingId: UUID,
        @ApiParam(value = "Id til journalpost")
        @PathVariable journalpostId: String,
        @ApiParam(value = "Id til dokumentInfo")
        @PathVariable dokumentInfoId: String

    ): ResponseEntity<ByteArray> {
        logger.debug(
            "Get getArkivertDokument is requested. behandlingsid: {} - journalpostId: {} - dokumentInfoId: {}",
            klagebehandlingId,
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
        value = "Henter metadata om dokumenter knyttet til en klagebehandling",
        notes = "Henter metadata om dokumenter knyttet til en klagebehandling. Berikes med data fra SAF."
    )
    @GetMapping("/{id}/dokumenttilknytninger", produces = ["application/json"])
    fun fetchConnectedDokumenter(
        @ApiParam(value = "Id til klagebehandlingen i vårt system")
        @PathVariable("id") klagebehandlingId: UUID
    ): DokumenterResponse {
        return klagebehandlingService.fetchJournalposterConnectedToKlagebehandling(klagebehandlingId)
    }

    @PostMapping("/{id}/dokumenttilknytninger")
    fun setTilknyttetDokument(
        @PathVariable("id") klagebehandlingId: UUID,
        @RequestBody input: TilknyttetDokument
    ): KlagebehandlingEditedView {
        logKlagebehandlingMethodDetails(
            "setTilknyttetDokument", innloggetSaksbehandlerRepository.getInnloggetIdent(), klagebehandlingId,
            logger
        )
        val modified = klagebehandlingService.connectDokumentToKlagebehandling(
            klagebehandlingId = klagebehandlingId,
            journalpostId = input.journalpostId,
            dokumentInfoId = input.dokumentInfoId,
            saksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return KlagebehandlingEditedView(modified = modified)
    }

    @DeleteMapping("/{id}/dokumenttilknytninger/{journalpostId}/{dokumentInfoId}")
    fun removeTilknyttetDokument(
        @PathVariable("id") klagebehandlingId: UUID,
        @PathVariable("journalpostId") journalpostId: String,
        @PathVariable("dokumentInfoId") dokumentInfoId: String
    ): KlagebehandlingEditedView {
        logKlagebehandlingMethodDetails(
            "removeTilknyttetDokument", innloggetSaksbehandlerRepository.getInnloggetIdent(), klagebehandlingId,
            logger
        )
        val modified = klagebehandlingService.disconnectDokumentFromKlagebehandling(
            klagebehandlingId = klagebehandlingId,
            journalpostId = journalpostId,
            dokumentInfoId = dokumentInfoId,
            saksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return KlagebehandlingEditedView(modified)
    }
}