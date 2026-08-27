package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.oppgave.api.view.FlowStateInput
import no.nav.klage.oppgave.api.view.FlowStateView
import no.nav.klage.oppgave.api.view.RolView
import no.nav.klage.oppgave.api.view.SaksbehandlerInput
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/behandlinger")
class BehandlingROLController(
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val behandlingService: BehandlingService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/{id}/rol")
    fun getROL(
        @PathVariable("id") behandlingId: UUID,
    ): RolView {
        logBehandlingMethodDetails(
            methodName = ::getROL.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )
        return behandlingService.getBehandlingROLView(behandlingId)
    }

    @GetMapping("/{id}/rolflowstate")
    fun getROLFlowState(
        @PathVariable("id") behandlingId: UUID,
    ): FlowStateView {
        logBehandlingMethodDetails(
            methodName = ::getROLFlowState.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )
        return FlowStateView(flowState = behandlingService.getBehandlingAndCheckReadAccessToSak(behandlingId).rolFlowState)
    }

    @PutMapping("/{behandlingId}/rolnavident")
    fun setROLIdent(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: SaksbehandlerInput,
    ): RolView {
        logBehandlingMethodDetails(
            methodName = ::setROLIdent.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        return behandlingService.setROLIdent(
            behandlingId = behandlingId,
            rolIdent = input.navIdent,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
        )
    }

    @PutMapping("/{behandlingId}/rolflowstate")
    fun setROLFlowState(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: FlowStateInput,
    ): RolView {
        logBehandlingMethodDetails(
            methodName = ::setROLFlowState.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        if (input.flowState in listOf(FlowState.RETURNED_APPROVED, FlowState.RETURNED_NOT_APPROVED)) {
            throw IllegalArgumentException("ROL skal ikke bruke denne flyten: ${input.flowState}. Kontakt Team Klage.")
        }

        return behandlingService.setROLFlowState(
            behandlingId = behandlingId,
            flowState = input.flowState,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
        )
    }
}
