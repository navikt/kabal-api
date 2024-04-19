package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.BehandlingerListResponse
import no.nav.klage.oppgave.api.view.EnhetensFerdigstilteOppgaverQueryParams
import no.nav.klage.oppgave.api.view.MineFerdigstilteOppgaverQueryParams
import no.nav.klage.oppgave.api.view.OppgaveView
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.OppgaveService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.logBehandlingMethodDetails
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
class OppgaveController(
    private val behandlingService: BehandlingService,
    private val behandlingMapper: BehandlingMapper,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val oppgaveService: OppgaveService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @GetMapping("/oppgaver/{behandlingId}")
    fun getOppgaveView(
        @PathVariable("behandlingId") behandlingId: UUID
    ): OppgaveView {
        logBehandlingMethodDetails(
            methodName = ::getOppgaveView.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            behandlingId = behandlingId,
            logger = logger,
        )

        return behandlingMapper.mapBehandlingToOppgaveView(
            behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        )
    }

    @GetMapping("/oppgaver/ferdigstilte", produces = ["application/json"])
    fun getMineFerdigstilteOppgaver(
        queryParams: MineFerdigstilteOppgaverQueryParams
    ): BehandlingerListResponse {
        logMethodDetails(
            methodName = ::getMineFerdigstilteOppgaver.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )
        secureLogger.debug("${::getMineFerdigstilteOppgaver.name} called with params: {}", queryParams)

        return oppgaveService.getFerdigstilteOppgaverForNavIdent(queryParams)
    }

    @GetMapping("/enheter/{enhetId}/oppgaver/tildelte/ferdigstilte", produces = ["application/json"])
    fun getEnhetensFerdigstilteOppgaver(
        @Parameter(name = "EnhetId til enheten den ansatte jobber i")
        @PathVariable enhetId: String,
        queryParams: EnhetensFerdigstilteOppgaverQueryParams
    ): BehandlingerListResponse {
        logMethodDetails(
            methodName = ::getEnhetensFerdigstilteOppgaver.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )
        validateRettigheterForEnhetensTildelteOppgaver()

        return oppgaveService.getFerdigstilteOppgaverForEnhet(queryParams)
    }

    private fun validateRettigheterForEnhetensTildelteOppgaver() {
        if (!innloggetSaksbehandlerService.hasKabalInnsynEgenEnhetRole()) {
            val message =
                "${innloggetSaksbehandlerService.getInnloggetIdent()} har ikke tilgang til å se enhetens oppgaver."
            logger.warn(message)
            throw MissingTilgangException(message)
        }
    }
}