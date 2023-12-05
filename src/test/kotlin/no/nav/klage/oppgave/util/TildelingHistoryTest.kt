package no.nav.klage.oppgave.util

import no.nav.klage.kodeverk.FlowState
import no.nav.klage.kodeverk.FradelingReason
import no.nav.klage.oppgave.domain.klage.MedunderskriverHistorikk
import no.nav.klage.oppgave.domain.klage.TildelingHistorikk
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class TildelingHistoryTest {

    @Test
    fun `createTildelingHistory should create correct history`() {
        val tildelingHistorikkSet = setOf(
            TildelingHistorikk(
                saksbehandlerident = null,
                enhet = null,
                tidspunkt = LocalDateTime.now().minusDays(4),
                fradelingReason = null,
                utfoerendeIdent = null,
                hjemmelIdList = null,
            ),
            TildelingHistorikk(
                saksbehandlerident = "saksbehandler1",
                enhet = "enhet1",
                tidspunkt = LocalDateTime.now().minusDays(3),
                fradelingReason = null,
                utfoerendeIdent = "utfoerende1",
                hjemmelIdList = null,
            ),
            TildelingHistorikk(
                saksbehandlerident = null,
                enhet = null,
                tidspunkt = LocalDateTime.now().minusDays(2),
                fradelingReason = FradelingReason.ANNET,
                utfoerendeIdent = "utfoerende1",
                hjemmelIdList = null,
            ),
            TildelingHistorikk(
                saksbehandlerident = "saksbehandler1",
                enhet = "enhet1",
                tidspunkt = LocalDateTime.now().minusDays(1),
                fradelingReason = null,
                utfoerendeIdent = "boss",
                hjemmelIdList = null,
            )
        )

        val result =
            createTildelingHistory(
                tildelingHistorikkSet = tildelingHistorikkSet,
                behandlingCreated = LocalDateTime.now().minusDays(10),
                originalHjemmelIdList = null,
            )

        println(result)
    }

    @Test
    fun `createMedunderskriverHistory should create correct history`() {
        val medunderskriverHistorikkSet = setOf(
            MedunderskriverHistorikk(
                saksbehandlerident = null,
                flowState = FlowState.NOT_SENT,
                tidspunkt = LocalDateTime.now().minusDays(10),
                utfoerendeIdent = "SYSTEM"
            ),
            MedunderskriverHistorikk(
                saksbehandlerident = "medunderskriver1",
                flowState = FlowState.NOT_SENT,
                tidspunkt = LocalDateTime.now().minusDays(9),
                utfoerendeIdent = "utfoerende1"
            ),
            MedunderskriverHistorikk(
                saksbehandlerident = "medunderskriver1",
                flowState = FlowState.SENT,
                tidspunkt = LocalDateTime.now().minusDays(8),
                utfoerendeIdent = "utfoerende1"
            ),
            MedunderskriverHistorikk(
                saksbehandlerident = "medunderskriver1",
                flowState = FlowState.RETURNED,
                tidspunkt = LocalDateTime.now().minusDays(7),
                utfoerendeIdent = "utfoerende1"
            ),
            MedunderskriverHistorikk(
                saksbehandlerident = "medunderskriver2",
                flowState = FlowState.NOT_SENT,
                tidspunkt = LocalDateTime.now().minusDays(6),
                utfoerendeIdent = "utfoerende1"
            ),
        )

        val result = createMedunderskriverHistory(
            medunderskriverHistorikkSet = medunderskriverHistorikkSet,
            behandlingCreated = LocalDateTime.now().minusDays(10)
        )

    }
}