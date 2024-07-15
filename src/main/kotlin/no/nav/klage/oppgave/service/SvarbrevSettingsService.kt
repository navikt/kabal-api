package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.api.view.SaksbehandlerView
import no.nav.klage.oppgave.api.view.SvarbrevSettingsView
import no.nav.klage.oppgave.api.view.UpdateSvarbrevSettingsInput
import no.nav.klage.oppgave.domain.klage.SvarbrevSettings
import no.nav.klage.oppgave.domain.klage.SvarbrevSettingsHistory
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.repositories.SvarbrevSettingsRepository
import no.nav.klage.oppgave.util.TokenUtil
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class SvarbrevSettingsService(
    private val svarbrevSettingsRepository: SvarbrevSettingsRepository,
    private val tokenUtil: TokenUtil,
    private val saksbehandlerService: SaksbehandlerService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService
) {
    fun getSvarbrevSettings(): List<SvarbrevSettingsView> {
        return svarbrevSettingsRepository.findAll()
            .sortedWith(compareBy<SvarbrevSettings> { it.ytelse.navn }.thenBy { it.type.id }).map { it.toView() }
    }

    fun getSvarbrevSettingsForYtelse(ytelse: Ytelse): List<SvarbrevSettingsView> {
        return svarbrevSettingsRepository.findAll().filter { it.ytelse == ytelse }.sortedBy { it.type }
            .map { it.toView() }
    }

    fun getSvarbrevSettingsForYtelseAndType(ytelse: Ytelse, type: Type): SvarbrevSettingsView {
        return svarbrevSettingsRepository.findAll().find { it.ytelse == ytelse && it.type == type }!!.toView()
    }

    fun updateSvarbrevSettings(
        id: UUID,
        updateSvarbrevSettingsInput: UpdateSvarbrevSettingsInput
    ): SvarbrevSettingsView {
        if (innloggetSaksbehandlerService.isKabalSvarbrevinnstillinger()) {
            val svarbrevSettings = svarbrevSettingsRepository.findById(id).get()
            svarbrevSettings.apply {
                behandlingstidUnits = updateSvarbrevSettingsInput.behandlingstidUnits
                behandlingstidUnitType = updateSvarbrevSettingsInput.behandlingstidUnitType
                customText = updateSvarbrevSettingsInput.customText.takeIf { it.isNotBlank() }
                shouldSend = updateSvarbrevSettingsInput.shouldSend
                createdBy = tokenUtil.getIdent()
                modified = LocalDateTime.now()
            }

            svarbrevSettings.history.add(svarbrevSettings.toHistory())

            return svarbrevSettings.toView()
        } else throw MissingTilgangException("Du har ikke tilgang til å oppdatere svarbrevinnstillinger. Ta kontakt med identansvarlig.")
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
            typeId = type.id,
            behandlingstidUnits = behandlingstidUnits,
            behandlingstidUnitType = behandlingstidUnitType,
            customText = customText,
            shouldSend = shouldSend,
            created = created,
            modified = modified,
            modifiedBy = SaksbehandlerView(
                navIdent = createdBy,
                navn = saksbehandlerService.getNameForIdentDefaultIfNull(createdBy),
            )
        )
    }

    private fun SvarbrevSettingsHistory.toView(): SvarbrevSettingsView {
        return SvarbrevSettingsView(
            id = id,
            ytelseId = ytelse.id,
            typeId = type.id,
            behandlingstidUnits = behandlingstidUnits,
            behandlingstidUnitType = behandlingstidUnitType,
            customText = customText,
            shouldSend = shouldSend,
            created = created,
            modified = created,
            modifiedBy = SaksbehandlerView(
                navIdent = createdBy,
                navn = saksbehandlerService.getNameForIdentDefaultIfNull(createdBy),
            )
        )
    }
}