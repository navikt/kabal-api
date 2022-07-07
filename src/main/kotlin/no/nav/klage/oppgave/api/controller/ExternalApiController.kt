package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import no.nav.klage.oppgave.api.view.OversendtKlageAnkeV3
import no.nav.klage.oppgave.api.view.OversendtKlageV2
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.service.AnkeITrygderettenbehandlingService
import no.nav.klage.oppgave.service.MottakService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@RestController
@Api(tags = ["kabal-api-external"])
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("api")
class ExternalApiController(
    private val mottakService: MottakService,
    private val ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @ApiOperation(
        value = "Send inn klage til klageinstans",
        notes = "Endepunkt for å registrere en klage/anke som skal behandles av klageinstans"
    )
    @PostMapping("/oversendelse/v2/klage")
    fun sendInnKlageV2(
        @ApiParam(value = "Oversendt klage")
        @Valid @RequestBody oversendtKlage: OversendtKlageV2
    ) {
        mottakService.createMottakForKlageV2(oversendtKlage)
    }

    @ApiOperation(
        value = "Send inn sak til klageinstans",
        notes = "Endepunkt for å registrere en klage/anke som skal behandles av klageinstans"
    )
    @PostMapping("/oversendelse/v3/sak")
    fun sendInnSakV3(
        @ApiParam(value = "Oversendt sak")
        @Valid @RequestBody oversendtKlageAnke: OversendtKlageAnkeV3
    ) {
        mottakService.createMottakForKlageAnkeV3(oversendtKlageAnke)
    }
//TODO: Legg til når FE er klare
//    @ApiOperation(
//        value = "Send inn anker i trygderetten til Kabal",
//        notes = "Endepunkt for å registrere anker som allerede har blitt oversendt til Trygderetten i Kabal"
//    )
//    @PostMapping("/ankeritrygderetten")
//    fun sendInnAnkeITrygderettenV1(
//        @Valid @RequestBody oversendtAnkeITrygderetten: OversendtAnkeITrygderettenV1
//    ) {
//        secureLogger.debug("Ankeitrygderetten data $oversendtAnkeITrygderetten sent to Kabal")
//        ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(oversendtAnkeITrygderetten)
//    }
}