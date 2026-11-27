package no.nav.klage.oppgave.domain.behandling.setters

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Sakstype
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingWithOptionalTrygderettenSaksnummer
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.behandling.setters.BehandlingITrygderettenSetters.setTrygderettenSaksnummer
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.exceptions.IllegalOperation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class BehandlingITrygderettenSettersTest {
    private val baseline = LocalDateTime.of(2026, 1, 1, 0, 0)

    @Test
    fun `setting an optional saksnummer updates the entity and returns a change event`() {
        optionalBehandlinger().forEach { behandling ->
            val event = behandling.setTrygderettenSaksnummer(nyVerdi = "2026123", saksbehandlerident = SAKSBEHANDLERIDENT)

            assertThat(behandling.trygderettenSaksnummer).isEqualTo("2026123")
            assertThat((behandling as Behandling).modified).isAfter(baseline)
            val changeEvent = event ?: error("Expected a change event")
            assertThat(changeEvent.changeList).hasSize(1)
            assertThat(changeEvent.changeList.single().felt)
                .isEqualTo(BehandlingChangedEvent.Felt.TRYGDERETTEN_SAKSNUMMER)
            assertThat(changeEvent.changeList.single().saksbehandlerident).isEqualTo(SAKSBEHANDLERIDENT)
        }
    }

    @Test
    fun `clearing an optional saksnummer updates the entity and returns a change event`() {
        optionalBehandlinger("2026123").forEach { behandling ->
            val event = behandling.setTrygderettenSaksnummer(nyVerdi = null, saksbehandlerident = SAKSBEHANDLERIDENT)

            assertThat(behandling.trygderettenSaksnummer).isNull()
            assertThat((behandling as Behandling).modified).isAfter(baseline)
            val changeEvent = event ?: error("Expected a change event")
            assertThat(changeEvent.changeList).hasSize(1)
            assertThat(changeEvent.changeList.single().felt)
                .isEqualTo(BehandlingChangedEvent.Felt.TRYGDERETTEN_SAKSNUMMER)
            assertThat(changeEvent.changeList.single().saksbehandlerident).isEqualTo(SAKSBEHANDLERIDENT)
        }
    }

    @Test
    fun `setting the same optional saksnummer returns null without changing modified`() {
        optionalBehandlinger("2026123").forEach { behandling ->
            val event = behandling.setTrygderettenSaksnummer(nyVerdi = "2026123", saksbehandlerident = SAKSBEHANDLERIDENT)

            assertThat(event).isNull()
            assertThat(behandling.trygderettenSaksnummer).isEqualTo("2026123")
            assertThat((behandling as Behandling).modified).isEqualTo(baseline)
        }
    }

    @Test
    fun `invalid optional saksnummer throws without changing entity state`() {
        optionalBehandlinger("2026123").forEach { behandling ->
            assertThatThrownBy {
                behandling.setTrygderettenSaksnummer(nyVerdi = "ugyldig", saksbehandlerident = SAKSBEHANDLERIDENT)
            }.isInstanceOf(IllegalOperation::class.java)

            assertThat(behandling.trygderettenSaksnummer).isEqualTo("2026123")
            assertThat((behandling as Behandling).modified).isEqualTo(baseline)
        }
    }

    private fun optionalBehandlinger(trygderettenSaksnummer: String? = null): List<BehandlingWithOptionalTrygderettenSaksnummer> =
        listOf(
            createAnke(trygderettenSaksnummer),
            createGjenopptak(trygderettenSaksnummer),
        )

    private fun createAnke(trygderettenSaksnummer: String?) =
        AnkeITrygderettenbehandlingFoer2027(
            sendtTilTrygderetten = baseline,
            trygderettenSaksnummer = trygderettenSaksnummer,
            klager = createKlager(),
            sakenGjelder = createSakenGjelder(),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.ANKE_I_TRYGDERETTEN_FOER_2027,
            kildeReferanse = "synthetic-reference",
            fagsystem = Fagsystem.AO01,
            fagsakId = null,
            sakstype = Sakstype.GENERELL_SAK,
            arkivsaksnummer = "SYNTHETIC-123",
            mottattKlageinstans = baseline,
            modified = baseline,
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

    private fun createGjenopptak(trygderettenSaksnummer: String?) =
        GjenopptakITrygderettenbehandlingFoer2027(
            sendtTilTrygderetten = baseline,
            trygderettenSaksnummer = trygderettenSaksnummer,
            klager = createKlager(),
            sakenGjelder = createSakenGjelder(),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027,
            kildeReferanse = "synthetic-reference",
            fagsystem = Fagsystem.AO01,
            fagsakId = null,
            sakstype = Sakstype.GENERELL_SAK,
            arkivsaksnummer = "SYNTHETIC-123",
            mottattKlageinstans = baseline,
            modified = baseline,
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

    private fun createKlager() =
        Klager(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.PERSON, value = "synthetic-test-id"),
        )

    private fun createSakenGjelder() =
        SakenGjelder(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.PERSON, value = "synthetic-test-id"),
        )

    private companion object {
        const val SAKSBEHANDLERIDENT = "Z123456"
    }
}
