package no.nav.klage.oppgave.api.controller.behandling

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
class BehandlingAssignmentController(
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val saksbehandlerService: SaksbehandlerService,
    private val behandlingService: BehandlingService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PutMapping("/behandlinger/{id}/saksbehandler")
    fun setSaksbehandler(
        @Parameter(description = "Id til en behandling")
        @PathVariable("id") behandlingId: UUID,
        @RequestBody saksbehandlerInput: SaksbehandlerInput
    ): SaksbehandlerViewWrapped {
        logger.debug("setSaksbehandler is requested for behandling: {}", behandlingId)

        val behandling = behandlingService.setSaksbehandler(
            behandlingId = behandlingId,
            tildeltSaksbehandlerIdent = saksbehandlerInput.navIdent,
            enhetId = if (saksbehandlerInput.navIdent != null) saksbehandlerService.getEnhetForSaksbehandler(
                saksbehandlerInput.navIdent
            ).enhetId else null,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        )
        return getSaksbehandlerViewWrapped(behandling = behandling)
    }

    @GetMapping("/behandlinger/{id}/saksbehandler")
    fun getSaksbehandler(
        @Parameter(description = "Id til en behandling")
        @PathVariable("id") behandlingId: UUID,
    ): SaksbehandlerViewWrapped {
        logger.debug("getSaksbehandler is requested for behandling: {}", behandlingId)

        val behandling = behandlingService.getBehandling(behandlingId)

        return getSaksbehandlerViewWrapped(behandling)
    }

    private fun getSaksbehandlerViewWrapped(behandling: Behandling): SaksbehandlerViewWrapped {
        return SaksbehandlerViewWrapped(
            saksbehandler = getSaksbehandlerView(behandling),
            modified = behandling.modified,
        )
    }

    private fun getSaksbehandlerView(behandling: Behandling): SaksbehandlerView? {
        val saksbehandlerView = if (behandling.tildeling?.saksbehandlerident == null) {
            null
        } else {
            SaksbehandlerView(
                navIdent = behandling.tildeling?.saksbehandlerident!!,
                navn = saksbehandlerService.getNameForIdent(behandling.tildeling?.saksbehandlerident!!),
            )
        }
        return saksbehandlerView
    }
}