package no.nav.klage.oppgave.repositories

import io.mockk.every
import io.mockk.mockk
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.domain.saksbehandler.Enhet
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerPersonligInfo
import no.nav.klage.oppgave.gateway.AxsysGateway
import no.nav.klage.oppgave.gateway.AzureGateway
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test


internal class SaksbehandlerInfoRepositoryTest {

    private val axsysGateway: AxsysGateway = mockk()
    private val msClient: AzureGateway = mockk()

    private val repo: SaksbehandlerRepository =
        SaksbehandlerRepository(msClient, axsysGateway, "", "", "", "", "", "", "")

    private fun personligInfo() = SaksbehandlerPersonligInfo(
        navIdent = "Z12345",
        azureId = "Whatever",
        fornavn = "Test",
        etternavn = "Saksbehandler",
        sammensattNavn = "Test Saksbehandler",
        epost = "test.saksbehandler@trygdeetaten.no",
        enhet = Enhet("4295", "KA Nord")
    )

    @Test
    fun harTilgangTilEnhetOgYtelse() {
        every { msClient.getDataOmInnloggetSaksbehandler() } returns personligInfo()

        val softly = SoftAssertions()
        softly.assertThat(repo.harTilgangTilEnhetOgYtelse("01010112345", "4295", Ytelse.OMS_OMP)).isEqualTo(true)
        softly.assertThat(repo.harTilgangTilEnhetOgYtelse("01010112345", "4290", Ytelse.SYK_SYK)).isEqualTo(false)
        softly.assertThat(repo.harTilgangTilEnhetOgYtelse("01010112345", "4294", Ytelse.OMS_OMP)).isEqualTo(false)
        softly.assertThat(repo.harTilgangTilEnhetOgYtelse("01010112345", "4203", Ytelse.SYK_SYK)).isEqualTo(false)
        softly.assertThat(repo.harTilgangTilEnhetOgYtelse("01010112345", "finnes_ikke", Ytelse.OMS_OMP))
            .isEqualTo(false)
        softly.assertAll()
    }

    @Test
    fun harTilgangTilYtelse() {
        every { msClient.getDataOmInnloggetSaksbehandler() } returns personligInfo()

        val softly = SoftAssertions()
        softly.assertThat(repo.harTilgangTilYtelse("01010112345", Ytelse.SYK_SYK)).isEqualTo(false)
        softly.assertThat(repo.harTilgangTilYtelse("01010112345", Ytelse.OMS_OLP)).isEqualTo(true)
        softly.assertAll()
    }
}