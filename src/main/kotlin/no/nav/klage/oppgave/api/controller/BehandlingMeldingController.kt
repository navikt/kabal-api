package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.view.MeldingInput
import no.nav.klage.oppgave.api.view.MeldingModified
import no.nav.klage.oppgave.api.view.MeldingView
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.MeldingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping(value = ["/klagebehandlinger", "/behandlinger"])
class BehandlingMeldingController(
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val meldingService: MeldingService,
    private val behandlingService: BehandlingService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Operation(
        summary = "Legg til ny melding til behandling",
        description = "Legger inn ny melding på en behandling",
    )
    @PostMapping("/{behandlingId}/meldinger")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMelding(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: MeldingInput,
    ): MeldingView {
        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        logBehandlingMethodDetails(
            methodName = ::addMelding.name,
            innloggetIdent = innloggetIdent,
            behandlingId = behandlingId,
            logger = logger,
        )

        validateAccessToBehandling(behandlingId)

        return meldingService.addMelding(
            behandlingId = behandlingId,
            innloggetIdent = innloggetIdent,
            text = input.text,
            notify = input.notify,
        )
    }

    @Operation(
        summary = "Hent alle meldinger på en behandling",
        description = "Henter alle meldinger på en behandling. Sist først.",
    )
    @GetMapping("/{behandlingId}/meldinger")
    fun getMeldinger(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): List<MeldingView> {
        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        logBehandlingMethodDetails(
            methodName = ::getMeldinger.name,
            innloggetIdent = innloggetIdent,
            behandlingId = behandlingId,
            logger = logger,
        )

        validateAccessToBehandling(behandlingId)

        return meldingService.getMeldingerForBehandling(behandlingId)
    }

    @Operation(
        summary = "Skru på varsel for melding",
        description = "Skru på varsel for melding",
    )
    @PostMapping("/{behandlingId}/meldinger/{meldingId}/notify")
    fun notifyMelding(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("meldingId") meldingId: UUID,
    ): MeldingModified {
        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        logBehandlingMethodDetails(
            methodName = ::notifyMelding.name,
            innloggetIdent = innloggetIdent,
            behandlingId = behandlingId,
            logger = logger,
        )

        validateAccessToBehandling(behandlingId)

        return meldingService.notifyMelding(
            behandlingId = behandlingId,
            innloggetIdent = innloggetIdent,
            meldingId = meldingId,
        )
    }

    private fun validateAccessToBehandling(behandlingId: UUID) {
        behandlingService.getBehandlingAndCheckReadAccessToSak(behandlingId)
    }
}
