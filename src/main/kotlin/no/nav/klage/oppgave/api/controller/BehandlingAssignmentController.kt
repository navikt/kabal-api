package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiParam
import no.nav.klage.oppgave.api.view.Saksbehandlertildeling
import no.nav.klage.oppgave.api.view.TildelingEditedView
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/ansatte")
class BehandlingAssignmentController(
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val saksbehandlerService: SaksbehandlerService,
    private val behandlingService: BehandlingService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    //TODO: Move this to KlagebehandlingController and URL /klagebehandlinger/{id}/saksbehandlerident , equal to medunderskriverident-operations?
    @PostMapping("/{navIdent}/klagebehandlinger/{id}/saksbehandlertildeling")
    fun assignSaksbehandler(
        @ApiParam(value = "NavIdent til en ansatt")
        @PathVariable navIdent: String,
        @ApiParam(value = "Id til en behandling")
        @PathVariable("id") behandlingId: UUID,
        @RequestBody saksbehandlertildeling: Saksbehandlertildeling
    ): TildelingEditedView {
        logger.debug("assignSaksbehandler is requested for behandling: {}", behandlingId)
        val behandling = behandlingService.assignBehandling(
            behandlingId,
            saksbehandlertildeling.navIdent,
            saksbehandlerService.getEnhetForSaksbehandler(saksbehandlertildeling.navIdent).enhetId,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return TildelingEditedView(
            behandling.modified,
            behandling.tildeling!!.tidspunkt.toLocalDate()
        )
    }

    @PostMapping("/{navIdent}/klagebehandlinger/{id}/saksbehandlerfradeling")
    fun unassignSaksbehandler(
        @ApiParam(value = "NavIdent til en ansatt")
        @PathVariable navIdent: String,
        @ApiParam(value = "Id til en behandling")
        @PathVariable("id") behandlingId: UUID
    ): TildelingEditedView {
        logger.debug("unassignSaksbehandler is requested for behandling: {}", behandlingId)
        val behandling = behandlingService.assignBehandling(
            behandlingId,
            null,
            null,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )

        return TildelingEditedView(
            behandling.modified,
            behandling.tildeling!!.tidspunkt.toLocalDate()
        )
    }
}

