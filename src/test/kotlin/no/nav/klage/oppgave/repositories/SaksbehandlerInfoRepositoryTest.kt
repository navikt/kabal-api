package no.nav.klage.oppgave.repositories

import io.mockk.every
import io.mockk.mockk
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.domain.saksbehandler.Enhet
import no.nav.klage.oppgave.gateway.AxsysGateway
import no.nav.klage.oppgave.gateway.AzureGateway
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test


internal class SaksbehandlerInfoRepositoryTest {

    private val axsysGateway: AxsysGateway = mockk()
    private val msClient: AzureGateway = mockk()

    private val repo: SaksbehandlerRepository =
        SaksbehandlerRepository(msClient, axsysGateway, "", "", "", "", "", "", "", "")

    @Test
    fun harTilgangTilEnhetOgYtelse() {
        every { axsysGateway.getEnheterForSaksbehandler("01010112345") } returns
                listOf(Enhet("4294", "KA Vest"), Enhet("4295", "KA Nord"))

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
    fun harTilgangTilEnhet() {
        every { axsysGateway.getEnheterForSaksbehandler("01010112345") } returns
                listOf(Enhet("4294", "KA Vest"), Enhet("4295", "KA Sør"))

        val softly = SoftAssertions()
        softly.assertThat(repo.harTilgangTilEnhet("01010112345", "4295")).isEqualTo(true)
        softly.assertThat(repo.harTilgangTilEnhet("01010112345", "4291")).isEqualTo(false)
        softly.assertAll()
    }

    @Test
    fun harTilgangTilYtelse() {
        every { axsysGateway.getEnheterForSaksbehandler("01010112345") } returns
                listOf(Enhet("4295", "KA Nord"))

        val softly = SoftAssertions()
        softly.assertThat(repo.harTilgangTilYtelse("01010112345", Ytelse.SYK_SYK)).isEqualTo(false)
        softly.assertThat(repo.harTilgangTilYtelse("01010112345", Ytelse.OMS_OLP)).isEqualTo(true)
        softly.assertAll()
    }
}