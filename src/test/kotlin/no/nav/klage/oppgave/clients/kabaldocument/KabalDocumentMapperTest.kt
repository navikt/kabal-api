package no.nav.klage.oppgave.clients.kabaldocument

import io.mockk.every
import io.mockk.mockk
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsHoveddokument
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Sakstype
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.model.request.TrygderettenMetadataInput
import no.nav.klage.oppgave.clients.klageunleashproxy.KlageUnleashProxyClient
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.DokDistKanalService
import no.nav.klage.oppgave.service.PersonService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class KabalDocumentMapperTest {
    private val personService = mockk<PersonService>(relaxed = true)
    private val eregClient = mockk<EregClient>(relaxed = true)
    private val dokDistKanalService = mockk<DokDistKanalService>(relaxed = true)
    private val behandlingService = mockk<BehandlingService>(relaxed = true)
    private val klageUnleashProxyClient = mockk<KlageUnleashProxyClient>()
    private val mapper =
        KabalDocumentMapper(
            personService = personService,
            eregClient = eregClient,
            dokDistKanalService = dokDistKanalService,
            behandlingService = behandlingService,
            klageUnleashProxyClient = klageUnleashProxyClient,
        )

    @Test
    fun `mapToTrygderettenMetadata maps optional saksnummer for pre-2027 appeal`() {
        assertTrygderettenSaksnummerIsMapped(
            behandling = createAnkeITrygderettenbehandling("2026123"),
            expectedSaksnummer = "2026123",
        )
        assertTrygderettenSaksnummerIsMapped(behandling = createAnkeITrygderettenbehandling(null), expectedSaksnummer = null)
    }

    @Test
    fun `mapToTrygderettenMetadata maps optional saksnummer for pre-2027 reopening`() {
        assertTrygderettenSaksnummerIsMapped(
            behandling = createGjenopptakITrygderettenbehandling("2026124"),
            expectedSaksnummer = "2026124",
        )
        assertTrygderettenSaksnummerIsMapped(behandling = createGjenopptakITrygderettenbehandling(null), expectedSaksnummer = null)
    }

    @Test
    fun `mapToTrygderettenMetadata retains mandatory post-2027 saksnummer`() {
        assertTrygderettenSaksnummerIsMapped(
            behandling = createMandatoryAnkeITrygderettenbehandling(),
            expectedSaksnummer = "2027123",
        )
    }

    private fun assertTrygderettenSaksnummerIsMapped(
        behandling: Behandling,
        expectedSaksnummer: String?,
    ) {
        every {
            klageUnleashProxyClient.isEnabled(feature = any(), navIdent = any())
        } returns true

        assertThat(mapToTrygderettenMetadata(behandling)?.trygderettenSaksnummer).isEqualTo(expectedSaksnummer)
    }

    private fun mapToTrygderettenMetadata(behandling: Behandling): TrygderettenMetadataInput? {
        val method =
            KabalDocumentMapper::class.java.getDeclaredMethod(
                "mapToTrygderettenMetadata",
                DokumentUnderArbeidAsHoveddokument::class.java,
                Behandling::class.java,
            )
        method.isAccessible = true
        return method.invoke(mapper, smartdokument(), behandling) as TrygderettenMetadataInput?
    }

    private fun smartdokument(): SmartdokumentUnderArbeidAsHoveddokument =
        mockk {
            every { dokumentType } returns DokumentType.EKSPEDISJONSBREV_TIL_TRYGDERETTEN
            every { markertFerdigBy } returns "TEST-IDENT"
            every { smartEditorTemplateId } returns "TEST_TEMPLATE"
        }

    private fun createAnkeITrygderettenbehandling(trygderettenSaksnummer: String?) =
        AnkeITrygderettenbehandlingFoer2027(
            sendtTilTrygderetten = LocalDateTime.of(2026, 1, 1, 0, 0),
            trygderettenSaksnummer = trygderettenSaksnummer,
            klager = testKlager(),
            sakenGjelder = testSakenGjelder(),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.ANKE_I_TRYGDERETTEN_FOER_2027,
            kildeReferanse = "test-reference",
            fagsystem = Fagsystem.AO01,
            fagsakId = "TEST-123",
            sakstype = Sakstype.FAGSAK,
            arkivsaksnummer = null,
            mottattKlageinstans = LocalDateTime.of(2026, 1, 1, 0, 0),
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

    private fun createGjenopptakITrygderettenbehandling(trygderettenSaksnummer: String?) =
        GjenopptakITrygderettenbehandlingFoer2027(
            sendtTilTrygderetten = LocalDateTime.of(2026, 1, 1, 0, 0),
            trygderettenSaksnummer = trygderettenSaksnummer,
            klager = testKlager(),
            sakenGjelder = testSakenGjelder(),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027,
            kildeReferanse = "test-reference",
            fagsystem = Fagsystem.AO01,
            fagsakId = "TEST-123",
            sakstype = Sakstype.FAGSAK,
            arkivsaksnummer = null,
            mottattKlageinstans = LocalDateTime.of(2026, 1, 1, 0, 0),
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

    private fun createMandatoryAnkeITrygderettenbehandling() =
        AnkeITrygderettenbehandlingEtter2027(
            sendtTilTrygderetten = LocalDateTime.of(2027, 1, 1, 0, 0),
            trygderettenSaksnummer = "2027123",
            klager = testKlager(),
            sakenGjelder = testSakenGjelder(),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.ANKE_I_TRYGDERETTEN_ETTER_2027,
            kildeReferanse = "test-reference",
            fagsystem = Fagsystem.AO01,
            fagsakId = "TEST-123",
            sakstype = Sakstype.FAGSAK,
            arkivsaksnummer = null,
            mottattKlageinstans = LocalDateTime.of(2027, 1, 1, 0, 0),
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

    private fun testKlager() =
        Klager(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.PERSON, value = "TEST-FNR"),
        )

    private fun testSakenGjelder() =
        SakenGjelder(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.PERSON, value = "TEST-FNR"),
        )
}
