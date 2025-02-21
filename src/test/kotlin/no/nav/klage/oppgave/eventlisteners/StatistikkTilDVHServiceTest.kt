package no.nav.klage.oppgave.eventlisteners

import io.mockk.mockk
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.service.StatistikkTilDVHService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class StatistikkTilDVHServiceTest {

    private val kafkaEventRepository: KafkaEventRepository = mockk()

    private val statistikkTilDVHService = StatistikkTilDVHService(
        kafkaEventRepository = kafkaEventRepository

    )


    @Test
    fun `shouldSendStats new behandling`() {

        val klagebehandlingEndretEvent = BehandlingEndretEvent(
            behandling = klagebehandlingOMP,
            endringslogginnslag = listOf(
                Endringslogginnslag(
                    saksbehandlerident = null,
                    felt = Felt.KLAGEBEHANDLING_MOTTATT,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        val ankebehandlingEndretEvent = BehandlingEndretEvent(
            behandling = ankebehandlingOMP,
            endringslogginnslag = listOf(
                Endringslogginnslag(
                    saksbehandlerident = null,
                    felt = Felt.ANKEBEHANDLING_MOTTATT,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        val ankeITrygderettenbehandlingEndretEvent = BehandlingEndretEvent(
            behandling = ankeITrygderettenbehandlingOMP,
            endringslogginnslag = listOf(
                Endringslogginnslag(
                    saksbehandlerident = null,
                    felt = Felt.ANKE_I_TRYGDERETTEN_OPPRETTET,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        assertTrue(statistikkTilDVHService.shouldSendStats(klagebehandlingEndretEvent))
        assertTrue(statistikkTilDVHService.shouldSendStats(ankebehandlingEndretEvent))
        assertFalse(statistikkTilDVHService.shouldSendStats(ankeITrygderettenbehandlingEndretEvent))
    }

    @Test
    fun `shouldSendStats existing behandling`() {

        val klagebehandlingEndretEvent = BehandlingEndretEvent(
            behandling = klagebehandlingOMP,
            endringslogginnslag = listOf(
                Endringslogginnslag(
                    saksbehandlerident = null,
                    felt = Felt.TILDELT_SAKSBEHANDLERIDENT,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        val klagebehandlingHJEEndretEvent = BehandlingEndretEvent(
            behandling = klagebehandlingHJE,
            endringslogginnslag = listOf(
                Endringslogginnslag(
                    saksbehandlerident = null,
                    felt = Felt.TILDELT_SAKSBEHANDLERIDENT,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        val ankebehandlingEndretEvent = BehandlingEndretEvent(
            behandling = ankebehandlingOMP,
            endringslogginnslag = listOf(
                Endringslogginnslag(
                    saksbehandlerident = null,
                    felt = Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        val ankeITrygderettenbehandlingEndretEvent = BehandlingEndretEvent(
            behandling = ankeITrygderettenbehandlingOMP,
            endringslogginnslag = listOf(
                Endringslogginnslag(
                    saksbehandlerident = null,
                    felt = Felt.AVSLUTTET_TIDSPUNKT,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        assertTrue(statistikkTilDVHService.shouldSendStats(klagebehandlingEndretEvent))
        assertFalse(statistikkTilDVHService.shouldSendStats(klagebehandlingHJEEndretEvent))
        assertTrue(statistikkTilDVHService.shouldSendStats(ankebehandlingEndretEvent))
        assertFalse(statistikkTilDVHService.shouldSendStats(ankeITrygderettenbehandlingEndretEvent))
    }

    private val klagebehandlingOMP = Klagebehandling(
        mottattVedtaksinstans = LocalDate.now(),
        previousSaksbehandlerident = null,
        avsenderEnhetFoersteinstans = "",
        kommentarFraFoersteinstans = null,
        mottakId = UUID.randomUUID(),
        klager = Klager(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ),
        ),
        sakenGjelder = SakenGjelder(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ),
        ),
        prosessfullmektig = null,
        ytelse = Ytelse.OMS_OMP,
        type = Type.KLAGE,
        kildeReferanse = "",
        dvhReferanse = null,
        fagsystem = Fagsystem.K9,
        fagsakId = "",
        mottattKlageinstans = LocalDateTime.now(),
        frist = LocalDate.now(),
        tildeling = null,
        tildelingHistorikk = mutableSetOf(),
        kakaKvalitetsvurderingId = UUID.randomUUID(),
        kakaKvalitetsvurderingVersion = 0,
        created = LocalDateTime.now(),
        modified = LocalDateTime.now(),
        saksdokumenter = mutableSetOf(),
        hjemler = setOf(),
        sattPaaVent = null,
        gosysOppgaveId = null,
        varsletBehandlingstid = null,
    )

    private val klagebehandlingHJE = Klagebehandling(
        mottattVedtaksinstans = LocalDate.now(),
        previousSaksbehandlerident = null,
        avsenderEnhetFoersteinstans = "",
        kommentarFraFoersteinstans = null,
        mottakId = UUID.randomUUID(),
        klager = Klager(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ),
        ),
        sakenGjelder = SakenGjelder(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ),
        ),
        prosessfullmektig = null,
        ytelse = Ytelse.HJE_HJE,
        type = Type.KLAGE,
        kildeReferanse = "",
        dvhReferanse = null,
        fagsystem = Fagsystem.IT01,
        fagsakId = "",
        mottattKlageinstans = LocalDateTime.now(),
        frist = LocalDate.now(),
        tildeling = null,
        tildelingHistorikk = mutableSetOf(),
        kakaKvalitetsvurderingId = UUID.randomUUID(),
        kakaKvalitetsvurderingVersion = 0,
        created = LocalDateTime.now(),
        modified = LocalDateTime.now(),
        saksdokumenter = mutableSetOf(),
        hjemler = setOf(),
        sattPaaVent = null,
        gosysOppgaveId = null,
        varsletBehandlingstid = null,
    )

    private val ankebehandlingOMP = Ankebehandling(
        mottakId = UUID.randomUUID(),
        klager = Klager(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ),
        ),
        sakenGjelder = SakenGjelder(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ),
        ),
        prosessfullmektig = null,
        ytelse = Ytelse.OMS_OMP,
        type = Type.ANKE,
        kildeReferanse = "",
        dvhReferanse = null,
        fagsystem = Fagsystem.K9,
        fagsakId = "",
        mottattKlageinstans = LocalDateTime.now(),
        frist = LocalDate.now(),
        tildeling = null,
        tildelingHistorikk = mutableSetOf(),
        kakaKvalitetsvurderingId = UUID.randomUUID(),
        kakaKvalitetsvurderingVersion = 0,
        created = LocalDateTime.now(),
        modified = LocalDateTime.now(),
        saksdokumenter = mutableSetOf(),
        hjemler = setOf(),
        sattPaaVent = null,
        klageVedtaksDato = null,
        klageBehandlendeEnhet = "",
        sourceBehandlingId = null,
        previousSaksbehandlerident = "C78901",
        gosysOppgaveId = null,
        varsletBehandlingstid = null,
    )

    private val ankeITrygderettenbehandlingOMP = AnkeITrygderettenbehandling(
        klager = Klager(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ),
        ),
        sakenGjelder = SakenGjelder(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ),
        ),
        prosessfullmektig = null,
        ytelse = Ytelse.OMS_OMP,
        type = Type.ANKE_I_TRYGDERETTEN,
        kildeReferanse = "",
        dvhReferanse = null,
        fagsystem = Fagsystem.K9,
        fagsakId = "",
        mottattKlageinstans = LocalDateTime.now(),
        frist = LocalDate.now(),
        tildeling = null,
        tildelingHistorikk = mutableSetOf(),
        created = LocalDateTime.now(),
        modified = LocalDateTime.now(),
        saksdokumenter = mutableSetOf(),
        hjemler = setOf(),
        sattPaaVent = null,
        sendtTilTrygderetten = LocalDateTime.now(),
        kjennelseMottatt = null,
        previousSaksbehandlerident = "C78901",
        gosysOppgaveId = null,
    )
}
