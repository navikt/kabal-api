package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.KlagebehandlingFullfoertView
import no.nav.klage.oppgave.api.view.ValidationPassedResponse
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.exceptions.FeatureNotEnabledException
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logKlagebehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/klagebehandlinger")
class KlagebehandlingController(
    private val klagebehandlingService: KlagebehandlingService,
    private val behandlingService: BehandlingService,
    private val behandlingMapper: BehandlingMapper,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/{id}/fullfoer")
    fun fullfoerKlagebehandling(
        @PathVariable("id") klagebehandlingId: UUID
    ): KlagebehandlingFullfoertView {
        logKlagebehandlingMethodDetails(
            ::fullfoerKlagebehandling.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )

        val behandling = behandlingService.getBehandling(klagebehandlingId)
        if (behandling.type == Type.ANKE) {
            throw FeatureNotEnabledException("fullfoer not implemented for anke")
        }

        val klagebehandling = klagebehandlingService.ferdigstillKlagebehandling(
            klagebehandlingId,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return behandlingMapper.mapToKlagebehandlingFullfoertView(klagebehandling)
    }

    /**
     * Valgfri validering før innsending/fullføring.
     * Gjøres uansett ved fullføring av behandlingen.
     */
    @GetMapping("/{id}/validate")
    fun validate(
        @PathVariable("id") klagebehandlingId: UUID
    ): ValidationPassedResponse {
        logKlagebehandlingMethodDetails(
            ::validate.name,
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )

        val behandling = behandlingService.getBehandling(klagebehandlingId)
        if (behandling.type == Type.ANKE) {
            throw FeatureNotEnabledException("validate not implemented for anke")
        }

        klagebehandlingService.validateKlagebehandlingBeforeFinalize(
            klagebehandlingService.getKlagebehandling(
                klagebehandlingId
            )
        )
        return ValidationPassedResponse()
    }
}