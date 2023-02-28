package no.nav.klage.oppgave.api.controller.behandling

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
import no.nav.klage.oppgave.util.logKlagebehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping(value = ["/klagebehandlinger", "/behandlinger"])
class BehandlingMedunderskriverController(
    private val behandlingMapper: BehandlingMapper,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val behandlingService: BehandlingService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PutMapping("/{behandlingId}/medunderskriverident")
    fun putMedunderskriverident(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: BehandlingMedunderskriveridentInput
    ): MedunderskriverFlytResponse {
        logBehandlingMethodDetails(
            ::putMedunderskriverident.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val behandling = behandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
            behandlingId,
            input.medunderskriverident,
            innloggetSaksbehandlerService.getInnloggetIdent()
        )
        return behandlingMapper.mapToMedunderskriverFlytResponse(behandling)
    }

    @PutMapping("/{behandlingId}/medunderskriver")
    fun putMedunderskriver(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: SaksbehandlerInput
    ): MedunderskriverWrapped {
        logBehandlingMethodDetails(
            ::putMedunderskriverident.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val behandling = behandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
            behandlingId,
            input.navIdent,
            innloggetSaksbehandlerService.getInnloggetIdent()
        )
        return behandlingMapper.mapToMedunderskriverWrapped(behandling)
    }

    @Operation(
        summary = "Flytter behandlingen mellom saksbehandler og medunderskriver.",
        description = "Flytter fra saksbehandler til medunderskriver dersom saksbehandler utfører, flytter til saksbehandler med returnert-status dersom medunderskriver utfører."
    )
    @PostMapping("/{behandlingId}/send")
    fun switchMedunderskriverFlyt(
        @Parameter(description = "Id til behandlingen i vårt system")
        @PathVariable("behandlingId") behandlingId: UUID
    ): MedunderskriverFlytResponse {
        logKlagebehandlingMethodDetails(
            ::switchMedunderskriverFlyt.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val behandling = behandlingService.switchMedunderskriverFlyt(
            behandlingId,
            innloggetSaksbehandlerService.getInnloggetIdent()
        )
        return behandlingMapper.mapToMedunderskriverFlytResponse(behandling)
    }

    @GetMapping("/{behandlingId}/medunderskriver")
    fun getMedunderskriver(
        @PathVariable("behandlingId") behandlingId: UUID
    ): MedunderskriverWrapped {
        logBehandlingMethodDetails(
            ::getMedunderskriver.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val behandling = behandlingService.getBehandling(behandlingId)
        return behandlingMapper.mapToMedunderskriverWrapped(behandling)
    }

    //TODO remove when frontend is updated and using /medunderskriver
    @GetMapping("/{id}/medunderskriverflyt")
    fun getMedunderskriverFlyt(
        @PathVariable("id") behandlingId: UUID
    ): MedunderskriverFlytView {
        logBehandlingMethodDetails(
            ::getMedunderskriverFlyt.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val behandling = behandlingService.getBehandling(behandlingId)
        return behandlingMapper.mapToMedunderskriverFlytView(behandling)
    }
}