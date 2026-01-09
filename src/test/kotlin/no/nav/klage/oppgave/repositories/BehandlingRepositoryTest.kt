package no.nav.klage.oppgave.repositories

import com.ninjasquad.springmockk.MockkBean
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Brevmottaker
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.db.PostgresIntegrationTestBase
import no.nav.klage.oppgave.domain.behandling.*
import no.nav.klage.oppgave.domain.behandling.embedded.*
import no.nav.klage.oppgave.domain.behandling.subentities.ForlengetBehandlingstidDraft
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.test.annotation.Commit
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.stream.Stream

@ActiveProfiles("local")
@DataJpaTest
class BehandlingRepositoryTest : PostgresIntegrationTestBase() {

    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    //Because of Hibernate Envers and our setup for audit logs.
    @MockkBean
    lateinit var tokenUtil: TokenUtil

    private val ENHET_1 = "ENHET_1"
    private val ENHET_2 = "ENHET_2"

    @ParameterizedTest
    @MethodSource("behandlingProvider")
    @Commit
    fun `trigger possible Hibernate envers errors for behandling`(behandling: Behandling) {
        behandlingRepository.save(behandling)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(behandlingRepository.findById(behandling.id).get()).isEqualTo(behandling)
    }

    @Test
    fun `store Klagebehandling works`() {
        val klage = getKlagebehandling()

        val forlengetBehandlingstidDraft = ForlengetBehandlingstidDraft()
        forlengetBehandlingstidDraft.title = "title"

        val receiver = Brevmottaker(
            technicalPartId = UUID.randomUUID(),
            identifikator = "abc",
            localPrint = false,
            forceCentralPrint = false,
            address = null,
            navn = "Test Navn"
        )

        forlengetBehandlingstidDraft.receivers.add(receiver)

        klage.forlengetBehandlingstidDraft = forlengetBehandlingstidDraft


        behandlingRepository.save(klage)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(behandlingRepository.findById(klage.id).get()).isEqualTo(klage)
    }

    @Test
    fun `store Klagebehandling with feilregistrering works`() {
        val klagebehandling = getKlagebehandling()

        klagebehandling.feilregistrering = Feilregistrering(
            navIdent = "navIdent",
            navn = "navn",
            registered = LocalDateTime.now(),
            reason = "reason",
            fagsystem = Fagsystem.K9,
        )

        behandlingRepository.save(klagebehandling)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(
            behandlingRepository.findById(klagebehandling.id).get().feilregistrering!!.navIdent
        ).isEqualTo(klagebehandling.feilregistrering!!.navIdent)
    }

    @Test
    fun `enhet based query works`() {
        val klageTildeltEnhet1 = getKlagebehandling()
        klageTildeltEnhet1.tildeling = Tildeling(
            saksbehandlerident = "1", enhet = ENHET_1, tidspunkt = LocalDateTime.now()
        )

        val klageTildeltEnhet2 = getKlagebehandling()
        klageTildeltEnhet2.tildeling = Tildeling(
            saksbehandlerident = "1", enhet = ENHET_2, tidspunkt = LocalDateTime.now()
        )

        val klageUtenTildeling = getKlagebehandling()
        val fullfoertKlage = getKlagebehandling()
        fullfoertKlage.tildeling = Tildeling(
            saksbehandlerident = "1", enhet = ENHET_1, tidspunkt = LocalDateTime.now()
        )
        fullfoertKlage.ferdigstilling = Ferdigstilling(
            avsluttetAvSaksbehandler = LocalDateTime.now(),
            navIdent = "navIdent",
            navn = "navn",
        )

        behandlingRepository.saveAll(listOf(klageTildeltEnhet1, klageTildeltEnhet2, klageUtenTildeling, fullfoertKlage))

        testEntityManager.flush()
        testEntityManager.clear()
        val result =
            behandlingRepository.findByTildelingEnhetAndFerdigstillingIsNullAndFeilregistreringIsNull(enhet = ENHET_1)

        assertThat(result).isEqualTo(listOf(klageTildeltEnhet1))
    }

    companion object {
        @JvmStatic
        fun behandlingProvider(): Stream<Behandling> = Stream.of(
            getKlagebehandling(),
            getAnkebehandling(),
            getAnkeITrygderettenbehandling(),
            getBehandlingEtterTrygderettenOpphevet(),
            getOmgjoeringskravbehandlingBasedOnKabalBehandling(),
            getOmgjoeringskravbehandlingBasedOnJournalpost(),
            getGjenopptaksbehandlingBasedOnJournalpost(),
            getGjenopptaksbehandlingBasedOnKabalBehandling(),
            getGjenopptakITrygderettenbehandling(),
        )

        private fun getKlagebehandling() = Klagebehandling(
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354")
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            fagsystem = Fagsystem.K9,
            fagsakId = "123",
            kildeReferanse = "abc",
            avsenderEnhetFoersteinstans = "0101",
            mottattVedtaksinstans = LocalDate.now(),
            kakaKvalitetsvurderingVersion = 2,
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

        private fun getAnkebehandling() = Ankebehandling(
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354")
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.ANKE,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            fagsystem = Fagsystem.K9,
            fagsakId = "123",
            kildeReferanse = "abc",
            klageBehandlendeEnhet = "4219",
            sourceBehandlingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 2,
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

        private fun getOmgjoeringskravbehandlingBasedOnKabalBehandling() = OmgjoeringskravbehandlingBasedOnKabalBehandling(
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354")
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.OMGJOERINGSKRAV,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            fagsystem = Fagsystem.K9,
            fagsakId = "123",
            kildeReferanse = "abc",
            klageBehandlendeEnhet = "4219",
            sourceBehandlingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 2,
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
            oppgaveId = null,

            )

        private fun getOmgjoeringskravbehandlingBasedOnJournalpost() = OmgjoeringskravbehandlingBasedOnJournalpost(
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354")
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.OMGJOERINGSKRAV,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            fagsystem = Fagsystem.K9,
            fagsakId = "123",
            kildeReferanse = "abc",
            klageBehandlendeEnhet = "4219",
            kakaKvalitetsvurderingVersion = 2,
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
            oppgaveId = null,
        )

        private fun getAnkeITrygderettenbehandling() = AnkeITrygderettenbehandling(
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354")
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.ANKE_I_TRYGDERETTEN,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            fagsystem = Fagsystem.K9,
            fagsakId = "123",
            kildeReferanse = "abc",
            sendtTilTrygderetten = LocalDateTime.now(),
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

        private fun getGjenopptakITrygderettenbehandling() = GjenopptakITrygderettenbehandling(
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354")
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            fagsystem = Fagsystem.K9,
            fagsakId = "123",
            kildeReferanse = "abc",
            sendtTilTrygderetten = LocalDateTime.now(),
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

        private fun getBehandlingEtterTrygderettenOpphevet() = BehandlingEtterTrygderettenOpphevet(
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354")
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            fagsystem = Fagsystem.K9,
            fagsakId = "123",
            kildeReferanse = "abc",
            kakaKvalitetsvurderingVersion = 2,
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            varsletBehandlingstid = null,
            sourceBehandlingId = UUID.randomUUID(),
            kjennelseMottatt = LocalDateTime.now(),
            ankeBehandlendeEnhet = "4219",
            forlengetBehandlingstidDraft = null,
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

        private fun getGjenopptaksbehandlingBasedOnJournalpost() = GjenopptaksbehandlingBasedOnJournalpost(
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354")
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.BEGJAERING_OM_GJENOPPTAK,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            fagsystem = Fagsystem.K9,
            fagsakId = "123",
            kildeReferanse = "abc",
            klageBehandlendeEnhet = "4219",
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 2,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

        private fun getGjenopptaksbehandlingBasedOnKabalBehandling() = GjenopptaksbehandlingBasedOnKabalBehandling(
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354")
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.BEGJAERING_OM_GJENOPPTAK,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            fagsystem = Fagsystem.K9,
            fagsakId = "123",
            kildeReferanse = "abc",
            sourceBehandlingId = UUID.randomUUID(),
            klageBehandlendeEnhet = "4219",
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 2,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )
    }

}
