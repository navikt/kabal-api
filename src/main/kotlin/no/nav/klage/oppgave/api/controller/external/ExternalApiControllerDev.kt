package no.nav.klage.oppgave.api.controller.external

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import no.nav.klage.oppgave.api.view.OversendtKlageAnkeV4
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.service.ExternalMottakFacade
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("dev-gcp")
@RestController
@Tag(
    name = "kabal-api-external",
    description = "Eksternt api for Kabal"
)
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("api")
class ExternalApiControllerDev(
    private val externalMottakFacade: ExternalMottakFacade,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Operation(
        summary = "Send inn sak til klageinstans",
        description = "Endepunkt for å registrere en klage/anke som skal behandles av klageinstans. OBS: Ikke i bruk enda."
    )
    @PostMapping("/oversendelse/v4/sak")
    fun sendInnSakV4(
        @Parameter(description = "Oversendt sak")
        @Valid @RequestBody oversendtKlageAnke: OversendtKlageAnkeV4
    ) {
        externalMottakFacade.createMottakForKlageAnkeV4(oversendtKlageAnke)
    }
}