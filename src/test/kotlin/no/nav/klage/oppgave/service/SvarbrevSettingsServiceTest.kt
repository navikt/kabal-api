package no.nav.klage.oppgave.service

import io.mockk.every
import io.mockk.mockk
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.domain.klage.SvarbrevSettings
import no.nav.klage.oppgave.repositories.SvarbrevSettingsRepository
import no.nav.klage.oppgave.util.TokenUtil
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SvarbrevSettingsServiceTest {

    private val svarbrevSettingsRepository: SvarbrevSettingsRepository = mockk()

    private val tokenUtil: TokenUtil = mockk()

    private val saksbehandlerService: SaksbehandlerService = mockk()

    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService = mockk()

    private val svarbrevSettingsService =
        SvarbrevSettingsService(
            svarbrevSettingsRepository = svarbrevSettingsRepository,
            tokenUtil = tokenUtil,
            saksbehandlerService = saksbehandlerService,
            innloggetSaksbehandlerService = innloggetSaksbehandlerService,
        )

    val inputSettings = listOf(
        SvarbrevSettings(
            ytelse = Ytelse.TIL_TIP,
            behandlingstidUnits = 12,
            behandlingstidUnitType = SvarbrevSettings.BehandlingstidUnitType.WEEKS,
            customText = null,
            shouldSend = true,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            createdBy = "Z123456",
            type = Type.KLAGE,
        ),
        SvarbrevSettings(
            ytelse = Ytelse.PEN_AFP,
            behandlingstidUnits = 12,
            behandlingstidUnitType = SvarbrevSettings.BehandlingstidUnitType.WEEKS,
            customText = null,
            shouldSend = true,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            createdBy = "Z123456",
            type = Type.ANKE,
        ),
        SvarbrevSettings(
            ytelse = Ytelse.TIL_TIP,
            behandlingstidUnits = 12,
            behandlingstidUnitType = SvarbrevSettings.BehandlingstidUnitType.WEEKS,
            customText = null,
            shouldSend = true,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            createdBy = "Z123456",
            type = Type.ANKE,
        ),
        SvarbrevSettings(
            ytelse = Ytelse.PEN_AFP,
            behandlingstidUnits = 12,
            behandlingstidUnitType = SvarbrevSettings.BehandlingstidUnitType.WEEKS,
            customText = null,
            shouldSend = true,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            createdBy = "Z123456",
            type = Type.KLAGE,
        ),
    )

    @Test
    fun `sortSvarbrevSettings`() {
        every { svarbrevSettingsRepository.findAll() }.returns(inputSettings)
        every { saksbehandlerService.getNameForIdentDefaultIfNull(any()) }.returns("Z123456")
        val getSvarbrevSettingsOutput = svarbrevSettingsService.getSvarbrevSettingsForYtelseAndType()

        assert(getSvarbrevSettingsOutput.size == 2)
        assert(getSvarbrevSettingsOutput[0].ytelseId == Ytelse.PEN_AFP.id)
        assert(getSvarbrevSettingsOutput[1].settings[1].typeId == Type.ANKE.id)
    }
}
