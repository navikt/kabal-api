package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.view.GosysOppgaveSearchInput
import no.nav.klage.oppgave.api.view.GosysOppgaveView
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.GosysOppgaveService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/gosysoppgaver")
class GosysOppgaveController(
    private val gosysOppgaveService: GosysOppgaveService,
    private val behandlingService: BehandlingService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Operation(
        summary = "Søk gosys-oppgaver for fnr og ytelse",
        description = "Henter Gosys-oppgaver for et fnr filtrert pa tema utledet fra ytelse."
    )
    @PostMapping(produces = ["application/json"])
    fun getGosysOppgaver(
        @RequestBody input: GosysOppgaveSearchInput,
    ): List<GosysOppgaveView> {
        logMethodDetails(
            ::getGosysOppgaver.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger,
        )

        return gosysOppgaveService.getGosysOppgaveList(
            fnr = input.fnr,
            tema = input.ytelse.toTema(),
        ).map {
            it.copy(
                alreadyUsedBy = behandlingService.findOpenBehandlingUsingGosysOppgave(it.id)
            )
        }
    }
}


