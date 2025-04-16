package no.nav.klage.oppgave.api.controller.external

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.service.AnkeITrygderettenbehandlingService
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
    private val ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Operation(
        summary = "Send inn klage til klageinstans",
        description = "Endepunkt for å registrere en klage/anke som skal behandles av klageinstans. Vurder å gå over til V4."
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
        description = "Endepunkt for å registrere en klage/anke som skal behandles av klageinstans. Vurder å gå over til V4."
    )
    @PostMapping("/oversendelse/v3/sak")
    fun sendInnSakV3(
        @Parameter(description = "Oversendt sak")
        @Valid @RequestBody oversendtKlageAnke: OversendtKlageAnkeV3
    ) {
        externalMottakFacade.createMottakForKlageAnkeV3(oversendtKlageAnke)
    }

    @Operation(
        summary = "Send inn sak til klageinstans",
        description = "Endepunkt for å registrere en klage/anke som skal behandles av klageinstans."
    )
    @PostMapping("/oversendelse/v4/sak")
    fun sendInnSakV4(
        @Parameter(description = "Oversendt sak")
        @Valid @RequestBody oversendtKlageAnke: OversendtKlageAnkeV4
    ) {
        externalMottakFacade.createMottakForKlageAnkeV4(oversendtKlageAnke)
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

    @Operation(
        summary = "Send inn anker i trygderetten til Kabal",
        description = "Endepunkt for å registrere anker som allerede har blitt oversendt til Trygderetten"
    )
    @PostMapping("/ankeritrygderetten")
    fun sendInnAnkeITrygderettenV1(
        @Valid @RequestBody oversendtAnkeITrygderetten: OversendtAnkeITrygderettenV1
    ) {
        secureLogger.debug("Ankeitrygderetten data $oversendtAnkeITrygderetten sent to Kabal")
        ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFromExternalApi(oversendtAnkeITrygderetten)
    }
}