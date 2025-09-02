package no.nav.klage.oppgave.eventlisteners

import io.mockk.mockk
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.behandling.Ankebehandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
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

        val klagebehandlingChangedEvent = BehandlingChangedEvent(
            behandling = klagebehandlingOMP,
            changeList = listOf(
                BehandlingChangedEvent.Change(
                    saksbehandlerident = null,
                    felt = BehandlingChangedEvent.Felt.KLAGEBEHANDLING_MOTTATT,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        val ankebehandlingChangedEvent = BehandlingChangedEvent(
            behandling = ankebehandlingOMP,
            changeList = listOf(
                BehandlingChangedEvent.Change(
                    saksbehandlerident = null,
                    felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_MOTTATT,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        val ankeITrygderettenbehandlingChangedEvent = BehandlingChangedEvent(
            behandling = ankeITrygderettenbehandlingOMP,
            changeList = listOf(
                BehandlingChangedEvent.Change(
                    saksbehandlerident = null,
                    felt = BehandlingChangedEvent.Felt.ANKE_I_TRYGDERETTEN_OPPRETTET,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        assertTrue(statistikkTilDVHService.shouldSendStats(klagebehandlingChangedEvent))
        assertTrue(statistikkTilDVHService.shouldSendStats(ankebehandlingChangedEvent))
        assertFalse(statistikkTilDVHService.shouldSendStats(ankeITrygderettenbehandlingChangedEvent))
    }

    @Test
    fun `shouldSendStats existing behandling`() {

        val klagebehandlingChangedEvent = BehandlingChangedEvent(
            behandling = klagebehandlingOMP,
            changeList = listOf(
                BehandlingChangedEvent.Change(
                    saksbehandlerident = null,
                    felt = BehandlingChangedEvent.Felt.TILDELT_SAKSBEHANDLERIDENT,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        val klagebehandlingHJEEndretEvent = BehandlingChangedEvent(
            behandling = klagebehandlingHJE,
            changeList = listOf(
                BehandlingChangedEvent.Change(
                    saksbehandlerident = null,
                    felt = BehandlingChangedEvent.Felt.TILDELT_SAKSBEHANDLERIDENT,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        val ankebehandlingChangedEvent = BehandlingChangedEvent(
            behandling = ankebehandlingOMP,
            changeList = listOf(
                BehandlingChangedEvent.Change(
                    saksbehandlerident = null,
                    felt = BehandlingChangedEvent.Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        val ankeITrygderettenbehandlingChangedEvent = BehandlingChangedEvent(
            behandling = ankeITrygderettenbehandlingOMP,
            changeList = listOf(
                BehandlingChangedEvent.Change(
                    saksbehandlerident = null,
                    felt = BehandlingChangedEvent.Felt.AVSLUTTET_TIDSPUNKT,
                    behandlingId = UUID.randomUUID(),
                )
            )
        )

        assertTrue(statistikkTilDVHService.shouldSendStats(klagebehandlingChangedEvent))
        assertFalse(statistikkTilDVHService.shouldSendStats(klagebehandlingHJEEndretEvent))
        assertTrue(statistikkTilDVHService.shouldSendStats(ankebehandlingChangedEvent))
        assertFalse(statistikkTilDVHService.shouldSendStats(ankeITrygderettenbehandlingChangedEvent))
    }

    private val klagebehandlingOMP = Klagebehandling(
        mottattVedtaksinstans = LocalDate.now(),
        previousSaksbehandlerident = null,
        avsenderEnhetFoersteinstans = "",
        kommentarFraFoersteinstans = null,
        mottakId = UUID.randomUUID(),
        klager = Klager(
            id = UUID.randomUUID(),
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ),
        ),
        sakenGjelder = SakenGjelder(
            id = UUID.randomUUID(),
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
        forlengetBehandlingstidDraft = null,
    )

    private val klagebehandlingHJE = Klagebehandling(
        mottattVedtaksinstans = LocalDate.now(),
        previousSaksbehandlerident = null,
        avsenderEnhetFoersteinstans = "",
        kommentarFraFoersteinstans = null,
        mottakId = UUID.randomUUID(),
        klager = Klager(
            id = UUID.randomUUID(),
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ),
        ),
        sakenGjelder = SakenGjelder(
            id = UUID.randomUUID(),
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
        forlengetBehandlingstidDraft = null,
    )

    private val ankebehandlingOMP = Ankebehandling(
        mottakId = UUID.randomUUID(),
        klager = Klager(
            id = UUID.randomUUID(),
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ),
        ),
        sakenGjelder = SakenGjelder(
            id = UUID.randomUUID(),
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
        forlengetBehandlingstidDraft = null,
    )

    private val ankeITrygderettenbehandlingOMP = AnkeITrygderettenbehandling(
        klager = Klager(
            id = UUID.randomUUID(),
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ),
        ),
        sakenGjelder = SakenGjelder(
            id = UUID.randomUUID(),
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
