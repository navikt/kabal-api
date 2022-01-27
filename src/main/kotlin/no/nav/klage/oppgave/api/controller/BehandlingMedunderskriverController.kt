package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.BehandlingMedunderskriveridentInput
import no.nav.klage.oppgave.api.view.MedunderskriverFlytResponse
import no.nav.klage.oppgave.api.view.MedunderskriverFlytView
import no.nav.klage.oppgave.api.view.MedunderskriverView
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
import no.nav.klage.oppgave.util.logKlagebehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/klagebehandlinger")
class BehandlingMedunderskriverController(
    private val behandlingMapper: BehandlingMapper,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val behandlingService: BehandlingService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PutMapping("/{id}/medunderskriverident")
    fun putMedunderskriverident(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody input: BehandlingMedunderskriveridentInput
    ): MedunderskriverFlytResponse {
        logBehandlingMethodDetails(
            ::putMedunderskriverident.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val behandling = behandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
            behandlingId,
            input.medunderskriverident,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return behandlingMapper.mapToMedunderskriverFlytResponse(behandling)
    }

    @ApiOperation(
        value = "Flytter behandlingen mellom saksbehandler og medunderskriver.",
        notes = "Flytter fra saksbehandler til medunderskriver dersom saksbehandler utfører, flytter til saksbehandler med returnert-status dersom medunderskriver utfører."
    )
    @PostMapping("/{id}/send")
    fun switchMedunderskriverFlyt(
        @ApiParam(value = "Id til behandlingen i vårt system")
        @PathVariable("id") behandlingId: UUID
    ): MedunderskriverFlytResponse {
        logKlagebehandlingMethodDetails(
            ::switchMedunderskriverFlyt.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val behandling = behandlingService.switchMedunderskriverFlyt(
            behandlingId,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return behandlingMapper.mapToMedunderskriverFlytResponse(behandling)
    }

    @GetMapping("/{id}/medunderskriver")
    fun getMedunderskriver(
        @PathVariable("id") behandlingId: UUID
    ): MedunderskriverView {
        logBehandlingMethodDetails(
            ::getMedunderskriver.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val behandling = behandlingService.getBehandling(behandlingId)
        return behandlingMapper.mapToMedunderskriverView(behandling)
    }

    @GetMapping("/{id}/medunderskriverflyt")
    fun getMedunderskriverFlyt(
        @PathVariable("id") behandlingId: UUID
    ): MedunderskriverFlytView {
        logBehandlingMethodDetails(
            ::getMedunderskriverFlyt.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            behandlingId,
            logger
        )
        val behandling = behandlingService.getBehandling(behandlingId)
        return behandlingMapper.mapToMedunderskriverFlytView(behandling)
    }
}