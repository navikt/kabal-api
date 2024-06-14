package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.SaksbehandlerView
import no.nav.klage.oppgave.api.view.SvarbrevSettingsView
import no.nav.klage.oppgave.api.view.UpdateSvarbrevSettingsInput
import no.nav.klage.oppgave.domain.klage.SvarbrevSettings
import no.nav.klage.oppgave.domain.klage.SvarbrevSettingsHistory
import no.nav.klage.oppgave.repositories.SvarbrevSettingsRepository
import no.nav.klage.oppgave.util.TokenUtil
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class SvarbrevSettingsService(
    private val svarbrevSettingsRepository: SvarbrevSettingsRepository,
    private val tokenUtil: TokenUtil,
    private val saksbehandlerService: SaksbehandlerService,
) {
    fun getSvarbrevSettings(): List<SvarbrevSettingsView> {
        return svarbrevSettingsRepository.findAll()
            .sortedBy { it.ytelse.navn }
            .map { it.toView() }
    }

    fun updateSvarbrevSettings(
        id: UUID,
        updateSvarbrevSettingsInput: UpdateSvarbrevSettingsInput
    ): SvarbrevSettingsView {
        val svarbrevSettings = svarbrevSettingsRepository.findById(id).get()
        svarbrevSettings.apply {
            behandlingstidWeeks = updateSvarbrevSettingsInput.behandlingstidWeeks
            customText = updateSvarbrevSettingsInput.customText
            shouldSend = updateSvarbrevSettingsInput.shouldSend
            createdBy = tokenUtil.getIdent()
        }

        svarbrevSettings.history.add(svarbrevSettings.toHistory())

        return svarbrevSettings.toView()
    }

    fun getSvarbrevSettingsHistory(id: UUID): List<SvarbrevSettingsView> {
        return svarbrevSettingsRepository.findById(id).get().history
            .sortedByDescending { it.created }
            .map { it.toView() }
    }

    private fun SvarbrevSettings.toView(): SvarbrevSettingsView {
        return SvarbrevSettingsView(
            id = id,
            ytelseId = ytelse.id,
            behandlingstidWeeks = behandlingstidWeeks,
            customText = customText,
            shouldSend = shouldSend,
            created = created,
            modified = modified,
            createdBy = SaksbehandlerView(
                navIdent = createdBy,
                navn = saksbehandlerService.getNameForIdentDefaultIfNull(createdBy),
            )
        )
    }

    private fun SvarbrevSettingsHistory.toView(): SvarbrevSettingsView {
        return SvarbrevSettingsView(
            id = id,
            ytelseId = ytelse.id,
            behandlingstidWeeks = behandlingstidWeeks,
            customText = customText,
            shouldSend = shouldSend,
            created = created,
            modified = created,
            createdBy = SaksbehandlerView(
                navIdent = createdBy,
                navn = saksbehandlerService.getNameForIdentDefaultIfNull(createdBy),
            )
        )
    }
}