package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.view.BehandlingEditedView
import no.nav.klage.oppgave.api.view.DokumenterResponse
import no.nav.klage.oppgave.api.view.TilknyttetDokument
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/behandlinger")
class BehandlingDokumentController(
    private val behandlingService: BehandlingService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Operation(
        summary = "Hent metadata om dokumenter for brukeren som saken gjelder"
    )
    @GetMapping("/{behandlingId}/arkivertedokumenter", produces = ["application/json"])
    fun fetchDokumenter(
        @Parameter(description = "Id til behandlingen i vårt system")
        @PathVariable("behandlingId") behandlingId: UUID,
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

    @PostMapping("/{id}/dokumenttilknytninger")
    fun setTilknyttetDokument(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: TilknyttetDokument
    ): BehandlingEditedView {
        logBehandlingMethodDetails(
            ::setTilknyttetDokument.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val modified = behandlingService.connectDocumentsToBehandling(
            behandlingId = behandlingId,
            journalfoertDokumentReferenceSet = setOf(
                JournalfoertDokumentReference(
                    journalpostId = input.journalpostId, dokumentInfoId = input.dokumentInfoId,
                )
            ),
            saksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            systemUserContext = false,
            ignoreCheckSkrivetilgang = false,
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
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val modified = behandlingService.disconnectDokumentFromBehandling(
            behandlingId = behandlingId,
            journalpostId = journalpostId,
            dokumentInfoId = dokumentInfoId,
            saksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )
        return BehandlingEditedView(modified)
    }
}