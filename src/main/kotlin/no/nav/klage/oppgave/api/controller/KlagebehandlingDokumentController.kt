package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import no.nav.klage.oppgave.api.view.DokumenterResponse
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.kodeverk.Tema
import no.nav.klage.oppgave.exceptions.BehandlingsidWrongFormatException
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.DokumentService
import no.nav.klage.oppgave.service.KlagebehandlingService
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
class KlagebehandlingDokumentController(
    private val klagebehandlingService: KlagebehandlingService,
    private val dokumentService: DokumentService,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @ApiOperation(
        value = "Hent dokumenter for en klagebehandling",
        notes = "Henter alle dokumenter om en person som saksbehandler har tilgang til."
    )
    @GetMapping("/{behandlingsid}/alledokumenter", produces = ["application/json"])
    fun fetchDokumenter(
        @ApiParam(value = "Id til klagebehandlingen i vårt system")
        @PathVariable behandlingsid: String,
        @RequestParam(required = false, name = "antall", defaultValue = "10") pageSize: Int,
        @RequestParam(required = false, name = "forrigeSide") previousPageRef: String? = null,
        @RequestParam(required = false, name = "tema") tema: List<String>? = emptyList()
    ): DokumenterResponse {
        val klagebehandlingId = parseAndValidate(behandlingsid)
        return klagebehandlingService.fetchDokumentlisteForKlagebehandling(
            klagebehandlingId = klagebehandlingId,
            temaer = tema?.map { Tema.of(it) } ?: emptyList(),
            pageSize = pageSize,
            previousPageRef = previousPageRef
        )
    }

    @ApiOperation(
        value = "Henter journalposter med dokumenter knyttet til en klagebehandling",
        notes = "Henter journalpostene til dokumentene som saksbehandler har markert at skal knyttes til klagebehandlingen."
    )
    @GetMapping("/{behandlingsid}/dokumenter", produces = ["application/json"])
    fun fetchConnectedDokumenter(
        @ApiParam(value = "Id til klagebehandlingen i vårt system")
        @PathVariable behandlingsid: String
    ): DokumenterResponse {
        val klagebehandlingId = parseAndValidate(behandlingsid)
        return klagebehandlingService.fetchJournalposterConnectedToKlagebehandling(klagebehandlingId)
    }

    @ResponseBody
    @GetMapping("/{behandlingsId}/journalposter/{journalpostId}/dokumenter/{dokumentInfoId}")
    fun getArkivertDokument(
        @ApiParam(value = "Id til klagebehandlingen i vårt system")
        @PathVariable behandlingsId: String,
        @ApiParam(value = "Id til journalpost")
        @PathVariable journalpostId: String,
        @ApiParam(value = "Id til dokumentInfo")
        @PathVariable dokumentInfoId: String

    ): ResponseEntity<ByteArray> {
        val klagebehandlingId = parseAndValidate(behandlingsId)
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

    private fun parseAndValidate(behandlingsid: String): UUID =
        try {
            UUID.fromString(behandlingsid)
        } catch (e: Exception) {
            logger.warn("Unable to parse uuid from $behandlingsid", e)
            throw BehandlingsidWrongFormatException("$behandlingsid is not a valid behandlingsid")
        }
}