package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.api.view.Medunderskriver
import no.nav.klage.oppgave.api.view.Medunderskrivere
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RestController
@Api(tags = ["kabal-api"])
class SaksbehandlerController(
    private val saksbehandlerService: SaksbehandlerService,
    private val environment: Environment,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @ApiOperation(
        value = "Hent medunderskriver for en ansatt",
        notes = "Henter alle medunderskrivere som saksbehandler er knyttet til for en gitt ytelse."
    )
    @GetMapping(
        "/medunderskrivere/ytelser/{ytelse}/enheter/{enhet}/ansatte/{navIdent}",
        produces = ["application/json"]
    )
    fun getMedunderskrivereForYtelse(
        @ApiParam(value = "Id for ytelse man trenger medunderskrivere for")
        @PathVariable ytelse: String,
        @ApiParam(value = "Enhetsnr for enhet saksbehandleren man skal finne medunderskriver til jobber i")
        @PathVariable enhet: String,
        @ApiParam(value = "NavIdent til saksbehandleren man skal finne medunderskriver til")
        @PathVariable navIdent: String,
    ): Medunderskrivere {
        logger.debug("getMedunderskrivereForYtelse is requested by $navIdent")
        return if (environment.activeProfiles.contains("prod-gcp")) {
            saksbehandlerService.getMedunderskrivere(navIdent, enhet, Ytelse.of(ytelse))
        } else Medunderskrivere(
            tema = null,
            ytelse = ytelse,
            medunderskrivere = listOf(
                Medunderskriver("Z994488", "F_Z994488, E_Z994488"),
                Medunderskriver("Z994330", "F_Z994330 E_Z994330"),
                Medunderskriver("Z994861", "F_Z994861 E_Z994861"),
                Medunderskriver("Z994864", "F_Z994864 E_Z994864"),
                Medunderskriver("Z994863", "F_Z994863 E_Z994863"),
                Medunderskriver("Z994862", "F_Z994862 E_Z994862"),
            ).filter { it.ident != navIdent }
        )
    }

}

