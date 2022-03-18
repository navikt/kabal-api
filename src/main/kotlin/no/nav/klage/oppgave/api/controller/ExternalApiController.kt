package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import no.nav.klage.oppgave.api.view.AnkeBasertPaaKlageInput
import no.nav.klage.oppgave.api.view.OversendtKlageAnkeV3
import no.nav.klage.oppgave.api.view.OversendtKlageV2
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.service.MottakService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

@RestController
@Api(tags = ["kabal-api-external"])
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("api")
class ExternalApiController(
    private val mottakService: MottakService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
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

    @ApiOperation(
        value = "Send inn ankesak til klageinstans basert på klage behandlet i Kabal.",
        notes = "Endepunkt for å registrere en anke hos klageinstans. Krever at forrige klage er behandlet i Kabal."
    )
    @PostMapping("/oversendelse/v3/ankebasertpaaklage")
    fun sendInnAnkeBasertPaaKabalKlage(
        @ApiParam(value = "Id på klage behandlet i Kabal")
        @Valid @RequestBody input: AnkeBasertPaaKlageInput
    ) {
        mottakService.createAnkeMottakBasertPaaKlagebehandlingId(input)
    }

    @ApiOperation(
        value = "Hent informasjon om en klagebehandling. Ikke implementert.",
        notes = "Endepunkt for å se detaljert informasjon om en klagebehandling. Ikke implementert."
    )
    @GetMapping("innsyn/v1/behandling/{id}")
    fun innsynBehandling(
        @ApiParam(value = "Id for klagebehandling")
        @PathVariable("id") behandlingId: String
    ): String {
        return "Not implemented yet"
    }
}