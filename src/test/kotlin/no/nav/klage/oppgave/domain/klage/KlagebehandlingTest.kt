package no.nav.klage.oppgave.domain.klage

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class KlagebehandlingTest {
    private val fnr = "12345678910"
    private val enhet = "ENHET"

    private fun getKlagebehandling(): Klagebehandling {
        return Klagebehandling(
            fagsystem = Fagsystem.AO01,
            fagsakId = "123",
            kildeReferanse = "abc",
            klager = Klager(PartId(PartIdType.PERSON, fnr)),
            sakenGjelder = SakenGjelder(
                partId = PartId(PartIdType.PERSON, fnr),
            ),
            prosessfullmektig = null,
            mottakId = UUID.randomUUID(),
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            mottattVedtaksinstans = LocalDate.now(),
            avsenderEnhetFoersteinstans = enhet,
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 2,
            frist = LocalDate.now().plusWeeks(12),
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
        )
    }

    @Nested
    inner class Status {
        @Test
        fun `status IKKE_TILDELT`() {
            val klagebehandling = getKlagebehandling()
            assertThat(klagebehandling.getStatus()).isEqualTo(Behandling.Status.IKKE_TILDELT)
        }

        @Test
        fun `status IKKE_TILDELT etter tidligere tildeling`() {
            val klagebehandling = getKlagebehandling()
            klagebehandling.tildeling =
                Tildeling(saksbehandlerident = null, enhet = null, tidspunkt = LocalDateTime.now())
            assertThat(klagebehandling.getStatus()).isEqualTo(Behandling.Status.IKKE_TILDELT)
        }

        @Test
        fun `status TILDELT`() {
            val klagebehandling = getKlagebehandling()
            klagebehandling.tildeling =
                Tildeling(saksbehandlerident = "abc", enhet = null, tidspunkt = LocalDateTime.now())
            assertThat(klagebehandling.getStatus()).isEqualTo(Behandling.Status.TILDELT)
        }

        @Test
        fun `status SENDT_TIL_MEDUNDERSKRIVER`() {
            val klagebehandling = getKlagebehandling()
            klagebehandling.medunderskriver = MedunderskriverTildeling("abc123", LocalDateTime.now())
            klagebehandling.medunderskriverFlowState = FlowState.SENT
            assertThat(klagebehandling.getStatus()).isEqualTo(Behandling.Status.SENDT_TIL_MEDUNDERSKRIVER)
        }

        @Test
        fun `status RETURNERT_TIL_SAKSBEHANDLER`() {
            val klagebehandling = getKlagebehandling()
            klagebehandling.medunderskriver = MedunderskriverTildeling("abc123", LocalDateTime.now())
            klagebehandling.medunderskriverFlowState = FlowState.RETURNED
            assertThat(klagebehandling.getStatus()).isEqualTo(Behandling.Status.RETURNERT_TIL_SAKSBEHANDLER)
        }

        @Test
        fun `status MEDUNDERSKRIVER_VALGT`() {
            val klagebehandling = getKlagebehandling()
            klagebehandling.medunderskriver = MedunderskriverTildeling("abc123", LocalDateTime.now())
            assertThat(klagebehandling.getStatus()).isEqualTo(Behandling.Status.MEDUNDERSKRIVER_VALGT)
        }

        @Test
        fun `status TILDELT når medunderskriver er fjernet`() {
            val klagebehandling = getKlagebehandling()
            klagebehandling.tildeling =
                Tildeling(saksbehandlerident = "abc", enhet = null, tidspunkt = LocalDateTime.now())
            klagebehandling.medunderskriver = MedunderskriverTildeling(null, LocalDateTime.now())
            assertThat(klagebehandling.getStatus()).isEqualTo(Behandling.Status.TILDELT)
        }

        @Test
        fun `status FULLFOERT`() {
            val klagebehandling = getKlagebehandling()
            klagebehandling.medunderskriver = MedunderskriverTildeling("abc123", LocalDateTime.now())
            klagebehandling.ferdigstilling = Ferdigstilling(
                avsluttet = LocalDateTime.now(),
                avsluttetAvSaksbehandler = LocalDateTime.now(),
                navIdent = "navIdent",
                navn = "navn",
            )
            assertThat(klagebehandling.getStatus()).isEqualTo(Behandling.Status.FULLFOERT)
        }
    }
}