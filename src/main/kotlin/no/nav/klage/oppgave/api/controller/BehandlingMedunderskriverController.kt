package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.oppgave.api.view.FlowStateView
import no.nav.klage.oppgave.api.view.MedunderskriverWrapped
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
class BehandlingMedunderskriverController(
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val behandlingService: BehandlingService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/{behandlingId}/medunderskriver")
    fun getMedunderskriver(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): MedunderskriverWrapped {
        logBehandlingMethodDetails(
            methodName = ::getMedunderskriver.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )
        return behandlingService.getMedunderskriver(behandlingId)
    }

    @GetMapping("/{id}/medunderskriverflowstate")
    fun getMedunderskriverFlowState(
        @PathVariable("id") behandlingId: UUID,
    ): FlowStateView {
        logBehandlingMethodDetails(
            methodName = ::getMedunderskriverFlowState.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )
        return behandlingService.getMedunderskriverFlowState(behandlingId)
    }

    @PutMapping("/{id}/medunderskriverflowstate")
    fun setMedunderskriverFlowState(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody medunderskriverFlowStateView: FlowStateView,
    ): MedunderskriverWrapped {
        logBehandlingMethodDetails(
            methodName = ::setMedunderskriverFlowState.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        if (medunderskriverFlowStateView.flowState == FlowState.RETURNED) {
            logger.error("Client using old FlowState")
            throw IllegalArgumentException("Klient er ikke oppdatert. Oppdater Kabal og prøv på nytt.")
        }

        return behandlingService.setMedunderskriverFlowState(
            behandlingId = behandlingId,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            flowState = medunderskriverFlowStateView.flowState,
        )
    }

    @PutMapping("/{id}/medunderskrivernavident")
    fun setMedunderskriverNavIdent(
        @PathVariable("id") behandlingId: UUID,
        @RequestBody medunderskriverNavIdent: SaksbehandlerInput,
    ): MedunderskriverWrapped {
        logBehandlingMethodDetails(
            methodName = ::setMedunderskriverNavIdent.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )
        return behandlingService.setMedunderskriverNavIdent(
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            navIdent = medunderskriverNavIdent.navIdent,
            behandlingId = behandlingId,
        )
    }
}
