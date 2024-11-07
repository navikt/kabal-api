package no.nav.klage.oppgave.api.controller.external

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import no.nav.klage.oppgave.api.view.ExternalFeilregistreringInput
import no.nav.klage.oppgave.api.view.OversendtKlageAnkeV3
import no.nav.klage.oppgave.api.view.OversendtKlageV2
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.ExternalMottakFacade
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(
    name = "kabal-api-external",
    description = "Eksternt api for Kabal"
)
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("api")
class ExternalApiController(
    private val externalMottakFacade: ExternalMottakFacade,
    private val behandlingService: BehandlingService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Operation(
        summary = "Send inn klage til klageinstans",
        description = "Endepunkt for å registrere en klage/anke som skal behandles av klageinstans"
    )
    @PostMapping("/oversendelse/v2/klage")
    fun sendInnKlageV2(
        @Parameter(description = "Oversendt klage")
        @Valid @RequestBody oversendtKlage: OversendtKlageV2
    ) {
        externalMottakFacade.createMottakForKlageV2(oversendtKlage)
    }

    @Operation(
        summary = "Send inn sak til klageinstans",
        description = "Endepunkt for å registrere en klage/anke som skal behandles av klageinstans"
    )
    @PostMapping("/oversendelse/v3/sak")
    fun sendInnSakV3(
        @Parameter(description = "Oversendt sak")
        @Valid @RequestBody oversendtKlageAnke: OversendtKlageAnkeV3
    ) {
        externalMottakFacade.createMottakForKlageAnkeV3(oversendtKlageAnke)
    }

    @Operation(
        summary = "Feilregistrer sak",
        description = "Endepunkt for å feilregistrere en klage/anke som ikke skal behandles av klageinstans. Fungerer kun hvis sak ikke er tildelt saksbehandler. Ellers må KA kontaktes."
    )
    @PostMapping("/feilregistrering")
    fun feilregistrer(
        @Parameter(description = "Feilregistrering")
        @Valid @RequestBody feilregistrering: ExternalFeilregistreringInput,
    ) {
        behandlingService.feilregistrer(
            type = feilregistrering.type,
            reason = feilregistrering.reason,
            navIdent = feilregistrering.navIdent,
            fagsystem = feilregistrering.fagsystem,
            kildereferanse = feilregistrering.kildereferanse,
        )
    }
}