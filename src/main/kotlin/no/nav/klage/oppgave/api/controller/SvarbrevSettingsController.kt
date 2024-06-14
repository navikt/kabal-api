package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.api.view.SvarbrevSettingsView
import no.nav.klage.oppgave.api.view.UpdateSvarbrevSettingsInput
import no.nav.klage.oppgave.service.SvarbrevSettingsService
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/svarbrev-settings")
class SvarbrevSettingsController(
    private val svarbrevSettingsService: SvarbrevSettingsService,
) {

    @GetMapping
    fun getSvarbrevSettings(): List<SvarbrevSettingsView> {
        return svarbrevSettingsService.getSvarbrevSettings()
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