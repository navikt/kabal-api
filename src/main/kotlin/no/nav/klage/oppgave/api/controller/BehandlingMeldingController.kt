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
import org.springframework.web.bind.annotation.*
import java.util.*

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
        description = "Legger inn ny melding på en behandling"
    )
    @PostMapping("/{behandlingId}/meldinger")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMelding(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestBody input: MeldingInput
    ): MeldingView {
        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        logBehandlingMethodDetails(
            ::addMelding.name,
            innloggetIdent,
            behandlingId,
            logger
        )

        validateAccessToBehandling(behandlingId)

        return meldingService.addMelding(
            behandlingId,
            innloggetIdent,
            input.text
        )
    }

    @Operation(
        summary = "Hent alle meldinger på en behandling",
        description = "Henter alle meldinger på en behandling. Sist først."
    )
    @GetMapping("/{behandlingId}/meldinger")
    fun getMeldinger(
        @PathVariable("behandlingId") behandlingId: UUID
    ): List<MeldingView> {
        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        logBehandlingMethodDetails(
            ::getMeldinger.name,
            innloggetIdent,
            behandlingId,
            logger
        )

        validateAccessToBehandling(behandlingId)

        return meldingService.getMeldingerForBehandling(behandlingId)
    }

    @Operation(
        summary = "Slett melding på en behandling",
        description = "Sletter en melding på en behandling"
    )
    @DeleteMapping("/{behandlingId}/meldinger/{meldingId}")
    fun deleteMelding(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("meldingId") meldingId: UUID
    ) {
        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        logBehandlingMethodDetails(
            ::deleteMelding.name,
            innloggetIdent,
            behandlingId,
            logger
        )

        validateAccessToBehandling(behandlingId)

        meldingService.deleteMelding(
            behandlingId,
            innloggetIdent,
            meldingId
        )
    }

    @Operation(
        summary = "Endre meldingstekst på en melding i en behandling",
        description = "Endrer tekst på en melding"
    )
    @PutMapping("/{behandlingId}/meldinger/{meldingId}")
    fun modifyMelding(
        @PathVariable("behandlingId") behandlingId: UUID,
        @PathVariable("meldingId") meldingId: UUID,
        @RequestBody input: MeldingInput
    ): MeldingModified {
        val innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        logBehandlingMethodDetails(
            ::modifyMelding.name,
            innloggetIdent,
            behandlingId,
            logger
        )

        validateAccessToBehandling(behandlingId)

        return meldingService.modifyMelding(
            behandlingId,
            innloggetIdent,
            meldingId,
            input.text
        )
    }

    private fun validateAccessToBehandling(behandlingId: UUID) {
        behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
    }

}