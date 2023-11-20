package no.nav.klage.oppgave.eventlisteners

import io.mockk.mockk
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.KafkaEventRepository
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
            endringslogginnslag = listOf()
        )

        val ankebehandlingEndretEvent = BehandlingEndretEvent(
            behandling = ankebehandlingOMP,
            endringslogginnslag = listOf()
        )

        val ankeITrygderettenbehandlingEndretEvent = BehandlingEndretEvent(
            behandling = ankeITrygderettenbehandlingOMP,
            endringslogginnslag = listOf()
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
                    kilde = KildeSystem.KABAL,
                    handling = Handling.ENDRING,
                    felt = Felt.TILDELT_SAKSBEHANDLERIDENT,
                    behandlingId = UUID.randomUUID(),
                    tidspunkt = LocalDateTime.now(),
                    fraVerdi = null,
                    tilVerdi = null,
                )
            )
        )

        val klagebehandlingHJEEndretEvent = BehandlingEndretEvent(
            behandling = klagebehandlingHJE,
            endringslogginnslag = listOf(
                Endringslogginnslag(
                    saksbehandlerident = null,
                    kilde = KildeSystem.KABAL,
                    handling = Handling.ENDRING,
                    felt = Felt.TILDELT_SAKSBEHANDLERIDENT,
                    behandlingId = UUID.randomUUID(),
                    tidspunkt = LocalDateTime.now(),
                    fraVerdi = null,
                    tilVerdi = null,
                )
            )
        )

        val ankebehandlingEndretEvent = BehandlingEndretEvent(
            behandling = ankebehandlingOMP,
            endringslogginnslag = listOf(
                Endringslogginnslag(
                    saksbehandlerident = null,
                    kilde = KildeSystem.KABAL,
                    handling = Handling.ENDRING,
                    felt = Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT,
                    behandlingId = UUID.randomUUID(),
                    tidspunkt = LocalDateTime.now(),
                    fraVerdi = null,
                    tilVerdi = null,
                )
            )
        )

        val ankeITrygderettenbehandlingEndretEvent = BehandlingEndretEvent(
            behandling = ankeITrygderettenbehandlingOMP,
            endringslogginnslag = listOf(
                Endringslogginnslag(
                    saksbehandlerident = null,
                    kilde = KildeSystem.KABAL,
                    handling = Handling.ENDRING,
                    felt = Felt.AVSLUTTET_TIDSPUNKT,
                    behandlingId = UUID.randomUUID(),
                    tidspunkt = LocalDateTime.now(),
                    fraVerdi = null,
                    tilVerdi = null,
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
        avsenderSaksbehandleridentFoersteinstans = null,
        avsenderEnhetFoersteinstans = "",
        kommentarFraFoersteinstans = null,
        mottakId = UUID.randomUUID(),
        innsendt = null,
        klager = Klager(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ), prosessfullmektig = null
        ),
        sakenGjelder = SakenGjelder(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ), skalMottaKopi = false
        ),
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
        sattPaaVent = null
    )

    private val klagebehandlingHJE = Klagebehandling(
        mottattVedtaksinstans = LocalDate.now(),
        avsenderSaksbehandleridentFoersteinstans = null,
        avsenderEnhetFoersteinstans = "",
        kommentarFraFoersteinstans = null,
        mottakId = UUID.randomUUID(),
        innsendt = null,
        klager = Klager(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ), prosessfullmektig = null
        ),
        sakenGjelder = SakenGjelder(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ), skalMottaKopi = false
        ),
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
        sattPaaVent = null
    )

    private val ankebehandlingOMP = Ankebehandling(
        mottakId = UUID.randomUUID(),
        innsendt = null,
        klager = Klager(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ), prosessfullmektig = null
        ),
        sakenGjelder = SakenGjelder(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ), skalMottaKopi = false
        ),
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
    )

    private val ankeITrygderettenbehandlingOMP = AnkeITrygderettenbehandling(
        klager = Klager(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ), prosessfullmektig = null
        ),
        sakenGjelder = SakenGjelder(
            partId = PartId(
                type = PartIdType.PERSON,
                value = ""
            ), skalMottaKopi = false
        ),
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
    )
}
