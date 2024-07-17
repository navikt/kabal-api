package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.kodeverk.FradelingReason
import no.nav.klage.oppgave.api.view.FradelSaksbehandlerInput
import no.nav.klage.oppgave.api.view.FradeltSaksbehandlerViewWrapped
import no.nav.klage.oppgave.api.view.SaksbehandlerViewWrapped
import no.nav.klage.oppgave.api.view.SetSaksbehandlerInput
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
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
        @RequestBody saksbehandlerInput: SetSaksbehandlerInput
    ): SaksbehandlerViewWrapped {
        logBehandlingMethodDetails(
            ::setSaksbehandler.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        val saksbehandler = behandlingService.setSaksbehandler(
            behandlingId = behandlingId,
            tildeltSaksbehandlerIdent = saksbehandlerInput.navIdent,
            enhetId = saksbehandlerService.getEnhetForSaksbehandler(
                saksbehandlerInput.navIdent
            ).enhetId,
            fradelingReason = null,
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
        )

        return saksbehandler
    }

    @PostMapping("/behandlinger/{id}/fradel")
    fun fradelSaksbehandler(
        @Parameter(description = "Id til en behandling")
        @PathVariable("id") behandlingId: UUID,
        @RequestBody saksbehandlerInput: FradelSaksbehandlerInput
    ): FradeltSaksbehandlerViewWrapped {
        logBehandlingMethodDetails(
            ::fradelSaksbehandler.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        behandlingService.fradelSaksbehandlerAndMaybeSetHjemler(
            behandlingId = behandlingId,
            tildeltSaksbehandlerIdent = null,
            enhetId = null,
            fradelingReason = FradelingReason.of(saksbehandlerInput.reasonId),
            utfoerendeSaksbehandlerIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            hjemmelIdList = saksbehandlerInput.hjemmelIdList,
        )

        return behandlingService.getFradeltSaksbehandlerViewWrapped(behandlingId)
    }

    @GetMapping("/behandlinger/{id}/saksbehandler")
    fun getSaksbehandler(
        @Parameter(description = "Id til en behandling")
        @PathVariable("id") behandlingId: UUID,
    ): SaksbehandlerViewWrapped {
        logBehandlingMethodDetails(
            ::getSaksbehandler.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId,
            logger
        )

        return behandlingService.getSaksbehandler(behandlingId)
    }
}