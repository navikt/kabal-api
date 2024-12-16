package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.view.SvarbrevSettingsConsumerView
import no.nav.klage.oppgave.api.view.SvarbrevSettingsView
import no.nav.klage.oppgave.api.view.UpdateSvarbrevSettingsInput
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.SvarbrevSettingsService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/svarbrev-settings")
class SvarbrevSettingsController(
    private val svarbrevSettingsService: SvarbrevSettingsService,
) {

    @GetMapping
    fun getSvarbrevSettings(): List<SvarbrevSettingsView> {
        return svarbrevSettingsService.getSvarbrevSettings()
    }

    @GetMapping("/ytelser/{ytelseId}/typer/{typeId}")
    fun getSvarbrevSettingsForYtelseAndType(
        @PathVariable("ytelseId") ytelseId: String,
        @PathVariable("typeId") typeId: String
    ): SvarbrevSettingsConsumerView {
        return svarbrevSettingsService.getSvarbrevSettingsViewForYtelseAndType(ytelse = Ytelse.of(ytelseId), type = Type.of(typeId))
    }

    @PutMapping("/{id}")
    fun updateSvarbrevSettings(
        @PathVariable id: UUID,
        @RequestBody input: UpdateSvarbrevSettingsInput
    ): SvarbrevSettingsView {
        return svarbrevSettingsService.updateSvarbrevSettings(id = id, updateSvarbrevSettingsInput = input)
    }

    @GetMapping("/{id}/history")
    fun getSvarbrevSettingsHistory(
        @PathVariable id: UUID,
    ): List<SvarbrevSettingsView> {
        return svarbrevSettingsService.getSvarbrevSettingsHistory(id = id)
    }
}