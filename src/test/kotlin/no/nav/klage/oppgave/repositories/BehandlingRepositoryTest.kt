package no.nav.klage.oppgave.repositories

import com.ninjasquad.springmockk.MockkBean
import jakarta.persistence.DiscriminatorValue
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Brevmottaker
import no.nav.klage.dokument.domain.dokumenterunderarbeid.OpplastetDokumentUnderArbeidAsHoveddokument
import no.nav.klage.kodeverk.DokumentType
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Sakstype
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.db.PostgresIntegrationTestBase
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingEtterTrygderettenOpphevet
import no.nav.klage.oppgave.domain.behandling.BehandlingRole
import no.nav.klage.oppgave.domain.behandling.BehandlingWithTrygderettenSaksnummer
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.Omgjoeringskravbehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Feilregistrering
import no.nav.klage.oppgave.domain.behandling.embedded.Ferdigstilling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.behandling.embedded.Tildeling
import no.nav.klage.oppgave.domain.behandling.historikk.TildelingHistorikk
import no.nav.klage.oppgave.domain.behandling.subentities.ForlengetBehandlingstidDraft
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.AuditReaderFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.test.annotation.Commit
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.transaction.TestTransaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.stream.Stream

@ActiveProfiles("local")
@DataJpaTest
class BehandlingRepositoryTest : PostgresIntegrationTestBase() {
    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    // Because of Hibernate Envers and our setup for audit logs.
    @MockkBean
    lateinit var tokenUtil: TokenUtil

    private val enhet1 = "ENHET_1"
    private val enhet2 = "ENHET_2"

    @ParameterizedTest
    @MethodSource("behandlingProvider")
    @Commit
    fun `trigger possible Hibernate envers errors for behandling`(behandling: Behandling) {
        behandlingRepository.save(behandling)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(behandlingRepository.findById(behandling.id).get()).isEqualTo(behandling)
    }

    @ParameterizedTest
    @MethodSource("consolidatedBehandlingProvider")
    fun `consolidated treatments round trip through repository and envers`(
        behandling: Behandling,
        expectedClass: Class<out Behandling>,
    ) {
        behandlingRepository.save(behandling)
        testEntityManager.flush()

        TestTransaction.flagForCommit()
        TestTransaction.end()
        TestTransaction.start()
        testEntityManager.clear()

        val stored = behandlingRepository.findById(behandling.id).get()
        assertThat(stored::class.java).isEqualTo(expectedClass)
        assertConsolidatedData(stored)

        val auditReader = AuditReaderFactory.get(testEntityManager.entityManager)
        val revisions = auditReader.getRevisions(Behandling::class.java, behandling.id)
        assertThat(revisions).isNotEmpty()

        val audited = auditReader.find(Behandling::class.java, behandling.id, revisions.last())
        assertThat(audited).isNotNull
        assertThat(audited!!::class.java).isEqualTo(expectedClass)
        assertConsolidatedData(audited)
    }

    private fun assertConsolidatedData(behandling: Behandling) {
        assertThat(behandling.kildeReferanse).isEqualTo("abc")
        when (behandling) {
            is GjenopptaksbehandlingFoer2027 -> {
                assertThat(behandling.paaanketVedtaksdato).isEqualTo(PAAANKET_VEDTAKSDATO)
                assertThat(behandling.forsterketRett).isTrue()
            }

            is Omgjoeringskravbehandling -> {
                assertThat(behandling.klageBehandlendeEnhet).isEqualTo("4219")
            }
        }
    }

    @Test
    fun `store Klagebehandling works`() {
        val klage = getKlagebehandling()

        val forlengetBehandlingstidDraft = ForlengetBehandlingstidDraft()
        forlengetBehandlingstidDraft.title = "title"

        val receiver =
            Brevmottaker(
                technicalPartId = UUID.randomUUID(),
                identifikator = "abc",
                localPrint = false,
                forceCentralPrint = false,
                address = null,
                navn = "Test Navn",
            )

        forlengetBehandlingstidDraft.receivers.add(receiver)

        klage.forlengetBehandlingstidDraft = forlengetBehandlingstidDraft

        behandlingRepository.save(klage)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(behandlingRepository.findById(klage.id).get()).isEqualTo(klage)
    }

    @Test
    fun `paaanketVedtaksdato and forsterketRett are persisted and read back`() {
        val ankebehandling = getAnkebehandling()

        behandlingRepository.save(ankebehandling)

        testEntityManager.flush()
        testEntityManager.clear()

        val stored = behandlingRepository.findById(ankebehandling.id).get() as AnkebehandlingFoer2027
        assertThat(stored.paaanketVedtaksdato).isEqualTo(PAAANKET_VEDTAKSDATO)
        assertThat(stored.forsterketRett).isTrue()
    }

    @Test
    fun `store Klagebehandling with feilregistrering works`() {
        val klagebehandling = getKlagebehandling()

        klagebehandling.feilregistrering =
            Feilregistrering(
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
            behandlingRepository
                .findById(klagebehandling.id)
                .get()
                .feilregistrering!!
                .navIdent,
        ).isEqualTo(klagebehandling.feilregistrering!!.navIdent)
    }

    @Test
    fun `behandling on generell sak without fagsakId is persisted and read back`() {
        val klagebehandling =
            getKlagebehandling(
                fagsakId = null,
                sakstype = Sakstype.GENERELL_SAK,
                arkivsaksnummer = "140411735",
            )

        behandlingRepository.save(klagebehandling)

        testEntityManager.flush()
        testEntityManager.clear()

        val stored = behandlingRepository.findById(klagebehandling.id).get()
        assertThat(stored.sakstype).isEqualTo(Sakstype.GENERELL_SAK)
        assertThat(stored.fagsakId).isNull()
        assertThat(stored.arkivsaksnummer).isEqualTo("140411735")
        assertThat(stored.getSaksnummer()).isEqualTo("140411735")
    }

    @Test
    fun `sakstype column defaults to FAGSAK for existing rows`() {
        val klagebehandling = getKlagebehandling(sakstype = Sakstype.GENERELL_SAK)

        behandlingRepository.save(klagebehandling)
        testEntityManager.flush()

        testEntityManager.entityManager
            .createNativeQuery("UPDATE klage.behandling SET sak_sakstype_id = DEFAULT WHERE id = :id")
            .setParameter("id", klagebehandling.id)
            .executeUpdate()
        testEntityManager.clear()

        val stored = behandlingRepository.findById(klagebehandling.id).get()
        assertThat(stored.sakstype).isEqualTo(Sakstype.FAGSAK)
        assertThat(stored.fagsakId).isEqualTo("123")
        assertThat(stored.arkivsaksnummer).isNull()
        assertThat(stored.getSaksnummer()).isEqualTo("123")
    }

    @Test
    fun `enhet based query works`() {
        val klageTildeltEnhet1 = getKlagebehandling()
        klageTildeltEnhet1.tildeling =
            Tildeling(
                saksbehandlerident = "1",
                enhet = enhet1,
                tidspunkt = LocalDateTime.now(),
            )

        val klageTildeltEnhet2 = getKlagebehandling()
        klageTildeltEnhet2.tildeling =
            Tildeling(
                saksbehandlerident = "1",
                enhet = enhet2,
                tidspunkt = LocalDateTime.now(),
            )

        val klageUtenTildeling = getKlagebehandling()
        val fullfoertKlage = getKlagebehandling()
        fullfoertKlage.tildeling =
            Tildeling(
                saksbehandlerident = "1",
                enhet = enhet1,
                tidspunkt = LocalDateTime.now(),
            )
        fullfoertKlage.ferdigstilling =
            Ferdigstilling(
                avsluttetAvSaksbehandler = LocalDateTime.now(),
                navIdent = "navIdent",
                navn = "navn",
            )

        behandlingRepository.saveAll(listOf(klageTildeltEnhet1, klageTildeltEnhet2, klageUtenTildeling, fullfoertKlage))

        testEntityManager.flush()
        testEntityManager.clear()
        val result =
            behandlingRepository.findByTildelingEnhetAndFerdigstillingIsNullAndFeilregistreringIsNull(enhet = enhet1)

        assertThat(result).isEqualTo(listOf(klageTildeltEnhet1))
    }

    @Test
    fun `find unassigned behandling i Trygderetten with kjennelse fra Trygderetten`() {
        val utenTildelingMedKjennelse = getAnkeITrygderettenbehandling()
        val utenTildelingMedAnnenPost = getAnkeITrygderettenbehandling()
        val utenTildelingUtenDokument = getAnkeITrygderettenbehandling()
        val tildeltMedKjennelse = getAnkeITrygderettenbehandling()
        tildeltMedKjennelse.tildeling =
            Tildeling(
                saksbehandlerident = "1",
                enhet = enhet1,
                tidspunkt = LocalDateTime.now(),
            )
        val ferdigstiltMedKjennelse = getAnkeITrygderettenbehandling()
        ferdigstiltMedKjennelse.ferdigstilling =
            Ferdigstilling(
                avsluttetAvSaksbehandler = LocalDateTime.now(),
                navIdent = "navIdent",
                navn = "navn",
            )

        behandlingRepository.saveAll(
            listOf(
                utenTildelingMedKjennelse,
                utenTildelingMedAnnenPost,
                utenTildelingUtenDokument,
                tildeltMedKjennelse,
                ferdigstiltMedKjennelse,
            ),
        )

        persistDokument(behandling = utenTildelingMedKjennelse, dokumentType = DokumentType.KJENNELSE_FRA_TRYGDERETTEN)
        persistDokument(
            behandling = utenTildelingMedAnnenPost,
            dokumentType = DokumentType.ANNEN_INNGAAENDE_POST,
            avsender = ORGNR_TRYGDERETTEN,
        )
        persistDokument(behandling = tildeltMedKjennelse, dokumentType = DokumentType.KJENNELSE_FRA_TRYGDERETTEN)
        persistDokument(behandling = ferdigstiltMedKjennelse, dokumentType = DokumentType.KJENNELSE_FRA_TRYGDERETTEN)

        testEntityManager.flush()
        testEntityManager.clear()

        val result = behandlingRepository.findUnassignedWithKjennelseFraTrygderetten()

        assertThat(result.map { it.behandlingId }).containsExactly(utenTildelingMedKjennelse.id)
        assertThat(result.single().fagsakId).isEqualTo(utenTildelingMedKjennelse.fagsakId)
    }

    @Test
    fun `find behandling i Trygderetten not held by saksbehandler after annen inngaaende post`() {
        val dokumentCreated = LocalDateTime.now().minusDays(10)

        val aldriTildelt = getAnkeITrygderettenbehandling()

        val tildeltFoerOgFradeltEtter = getAnkeITrygderettenbehandling()
        tildeltFoerOgFradeltEtter.addTildelingHistorikk(ident = "1", tidspunkt = dokumentCreated.minusDays(5))
        tildeltFoerOgFradeltEtter.addTildelingHistorikk(ident = null, tidspunkt = dokumentCreated.plusDays(2))

        val tildeltOgFradeltFoer = getAnkeITrygderettenbehandling()
        tildeltOgFradeltFoer.addTildelingHistorikk(ident = "1", tidspunkt = dokumentCreated.minusDays(5))
        tildeltOgFradeltFoer.addTildelingHistorikk(ident = null, tidspunkt = dokumentCreated.minusDays(2))

        val tildeltOgFradeltEtter = getAnkeITrygderettenbehandling()
        tildeltOgFradeltEtter.addTildelingHistorikk(ident = null, tidspunkt = tildeltOgFradeltEtter.created)
        tildeltOgFradeltEtter.addTildelingHistorikk(ident = "1", tidspunkt = dokumentCreated.plusDays(1))
        tildeltOgFradeltEtter.addTildelingHistorikk(ident = null, tidspunkt = dokumentCreated.plusDays(3))

        val tildeltNaa = getAnkeITrygderettenbehandling()
        tildeltNaa.tildeling =
            Tildeling(
                saksbehandlerident = "1",
                enhet = enhet1,
                tidspunkt = dokumentCreated.minusDays(1),
            )

        val annenPostFraAndre = getAnkeITrygderettenbehandling()
        val kjennelse = getAnkeITrygderettenbehandling()

        behandlingRepository.saveAll(
            listOf(
                aldriTildelt,
                tildeltFoerOgFradeltEtter,
                tildeltOgFradeltFoer,
                tildeltOgFradeltEtter,
                tildeltNaa,
                annenPostFraAndre,
                kjennelse,
            ),
        )

        listOf(aldriTildelt, tildeltFoerOgFradeltEtter, tildeltOgFradeltFoer, tildeltOgFradeltEtter, tildeltNaa)
            .forEach {
                persistDokument(
                    behandling = it,
                    dokumentType = DokumentType.ANNEN_INNGAAENDE_POST,
                    avsender = ORGNR_TRYGDERETTEN,
                    created = dokumentCreated,
                )
            }
        persistDokument(
            behandling = annenPostFraAndre,
            dokumentType = DokumentType.ANNEN_INNGAAENDE_POST,
            avsender = "123456789",
            created = dokumentCreated,
        )
        persistDokument(
            behandling = kjennelse,
            dokumentType = DokumentType.KJENNELSE_FRA_TRYGDERETTEN,
            created = dokumentCreated,
        )

        testEntityManager.flush()
        testEntityManager.clear()

        val result =
            behandlingRepository.findNotAssignedAfterAnnenInngaaendePostFraTrygderetten(
                organisasjonsnummerTrygderetten = ORGNR_TRYGDERETTEN,
            )

        assertThat(result.map { it.behandlingId }).containsExactlyInAnyOrder(
            aldriTildelt.id,
            tildeltOgFradeltFoer.id,
        )
    }

    @ParameterizedTest
    @MethodSource("trygderettenTypes")
    fun `approved duplicate check matches each supported type whether open or completed`(type: Type) {
        val openSaksnummer = syntheticTrygderettenSaksnummer()
        val completedSaksnummer = syntheticTrygderettenSaksnummer()
        behandlingRepository.saveAll(
            listOf(
                newTrygderettenBehandling(type = type, saksnummer = openSaksnummer),
                newTrygderettenBehandling(type = type, saksnummer = completedSaksnummer, completed = true),
            ),
        )

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(
            behandlingRepository.existsByTrygderettenSaksnummerAndFeilregistreringIsNull(
                trygderettenSaksnummer = openSaksnummer,
                behandlingTypes = trygderettenDiscriminatorValues(),
            ),
        ).isTrue()
        assertThat(
            behandlingRepository.existsByTrygderettenSaksnummerAndFeilregistreringIsNull(
                trygderettenSaksnummer = completedSaksnummer,
                behandlingTypes = trygderettenDiscriminatorValues(),
            ),
        ).isTrue()
    }

    @ParameterizedTest
    @MethodSource("trygderettenTypes")
    fun `approved duplicate check excludes feilregistrert rows for each supported type`(type: Type) {
        val saksnummer = syntheticTrygderettenSaksnummer()
        behandlingRepository.save(newTrygderettenBehandling(type = type, saksnummer = saksnummer, feilregistrert = true))

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(
            behandlingRepository.existsByTrygderettenSaksnummerAndFeilregistreringIsNull(
                trygderettenSaksnummer = saksnummer,
                behandlingTypes = trygderettenDiscriminatorValues(),
            ),
        ).isFalse()
    }

    @Test
    fun `approved duplicate check finds a valid row when a feilregistrert row shares its number`() {
        val saksnummer = syntheticTrygderettenSaksnummer()
        behandlingRepository.saveAll(
            listOf(
                newTrygderettenBehandling(type = Type.ANKE_ETTER_2027, saksnummer = saksnummer, feilregistrert = true),
                newTrygderettenBehandling(
                    type = Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_ETTER_2027,
                    saksnummer = saksnummer,
                    completed = true,
                ),
            ),
        )

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(
            behandlingRepository.existsByTrygderettenSaksnummerAndFeilregistreringIsNull(
                trygderettenSaksnummer = saksnummer,
                behandlingTypes = trygderettenDiscriminatorValues(),
            ),
        ).isTrue()
    }

    @Test
    fun `approved duplicate check returns false when no row matches`() {
        assertThat(
            behandlingRepository.existsByTrygderettenSaksnummerAndFeilregistreringIsNull(
                trygderettenSaksnummer = syntheticTrygderettenSaksnummer(),
                behandlingTypes = trygderettenDiscriminatorValues(),
            ),
        ).isFalse()
    }

    @Test
    fun `approved duplicate check ignores treatment types without Trygderetten number support`() {
        val behandling = getAnkeITrygderettenbehandling()
        val saksnummer = syntheticTrygderettenSaksnummer()
        behandlingRepository.save(behandling)
        testEntityManager.flush()
        testEntityManager.entityManager
            .createNativeQuery("UPDATE klage.behandling SET trygderetten_saksnummer = :saksnummer WHERE id = :id")
            .setParameter("saksnummer", saksnummer)
            .setParameter("id", behandling.id)
            .executeUpdate()
        testEntityManager.clear()

        assertThat(
            behandlingRepository.existsByTrygderettenSaksnummerAndFeilregistreringIsNull(
                trygderettenSaksnummer = saksnummer,
                behandlingTypes = trygderettenDiscriminatorValues(),
            ),
        ).isFalse()
    }

    @ParameterizedTest
    @MethodSource("exactMatchNearMisses")
    fun `approved duplicate check requires an exact number match`(
        storedSaksnummer: String,
        queriedSaksnummer: String,
    ) {
        behandlingRepository.save(
            newTrygderettenBehandling(type = Type.ANKE_ETTER_2027, saksnummer = storedSaksnummer),
        )

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(
            behandlingRepository.existsByTrygderettenSaksnummerAndFeilregistreringIsNull(
                trygderettenSaksnummer = queriedSaksnummer,
                behandlingTypes = trygderettenDiscriminatorValues(),
            ),
        ).isFalse()
    }

    @Test
    fun `approved duplicate check treats an injection-like value as ordinary unmatched input`() {
        assertThat(
            behandlingRepository.existsByTrygderettenSaksnummerAndFeilregistreringIsNull(
                trygderettenSaksnummer = "' OR 1=1 --",
                behandlingTypes = trygderettenDiscriminatorValues(),
            ),
        ).isFalse()
    }

    @Test
    fun `Trygderetten number implementors have usable unique discriminator values`() {
        val discriminatorValues = trygderettenDiscriminatorValues()

        assertThat(discriminatorValues).doesNotHaveDuplicates()
        assertThat(discriminatorValues).allSatisfy { assertThat(it).isNotBlank() }
    }

    @Test
    fun `approved duplicate check applies the supplied treatment type filter`() {
        val saksnummer = syntheticTrygderettenSaksnummer()
        val matchingType =
            checkNotNull(AnkebehandlingEtter2027::class.java.getAnnotation(DiscriminatorValue::class.java)?.value) {
                "Missing @DiscriminatorValue on ${AnkebehandlingEtter2027::class.java.name}"
            }
        check(matchingType.isNotBlank()) {
            "Blank @DiscriminatorValue on ${AnkebehandlingEtter2027::class.java.name}"
        }
        behandlingRepository.save(newTrygderettenBehandling(type = Type.ANKE_ETTER_2027, saksnummer = saksnummer))
        testEntityManager.flush()
        testEntityManager.clear()

        val discriminatorValues = trygderettenDiscriminatorValues()
        assertThat(discriminatorValues).contains(matchingType)
        assertThat(
            behandlingRepository.existsByTrygderettenSaksnummerAndFeilregistreringIsNull(
                trygderettenSaksnummer = saksnummer,
                behandlingTypes = discriminatorValues.filterNot { it == matchingType },
            ),
        ).isFalse()
        assertThat(
            behandlingRepository.existsByTrygderettenSaksnummerAndFeilregistreringIsNull(
                trygderettenSaksnummer = saksnummer,
                behandlingTypes = listOf(matchingType),
            ),
        ).isTrue()
    }

    private fun trygderettenDiscriminatorValues(): List<String> {
        val implementationClasses =
            testEntityManager.entityManager.metamodel.entities
                .map { it.javaType }
                .filter { BehandlingWithTrygderettenSaksnummer::class.java.isAssignableFrom(it) }
        check(implementationClasses.isNotEmpty()) {
            "No entities implement ${BehandlingWithTrygderettenSaksnummer::class.java.name}"
        }
        return implementationClasses.map { implementationClass ->
            val discriminatorValue =
                checkNotNull(implementationClass.getAnnotation(DiscriminatorValue::class.java)?.value) {
                    "Missing @DiscriminatorValue on ${implementationClass.name}"
                }
            check(discriminatorValue.isNotBlank()) {
                "Blank @DiscriminatorValue on ${implementationClass.name}"
            }
            discriminatorValue
        }
    }

    private fun newTrygderettenBehandling(
        type: Type,
        saksnummer: String,
        feilregistrert: Boolean = false,
        completed: Boolean = false,
    ): Behandling {
        val now = LocalDateTime.now()
        val feilregistrering =
            if (feilregistrert) {
                Feilregistrering(
                    navIdent = "TEST",
                    navn = "Test",
                    registered = now,
                    reason = "Test fixture",
                    fagsystem = Fagsystem.K9,
                )
            } else {
                null
            }
        val ferdigstilling =
            if (completed) {
                Ferdigstilling(
                    avsluttetAvSaksbehandler = now,
                    navIdent = "TEST",
                    navn = "Test",
                )
            } else {
                null
            }
        val klager =
            Klager(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "synthetic-${UUID.randomUUID()}"),
            )
        val sakenGjelder =
            SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "synthetic-${UUID.randomUUID()}"),
            )

        return when (type) {
            Type.ANKE_ETTER_2027 -> {
                AnkebehandlingEtter2027(
                    trygderettenSaksnummer = saksnummer,
                    klageBehandlendeEnhet = "4219",
                    previousBehandlingId = null,
                    klager = klager,
                    sakenGjelder = sakenGjelder,
                    prosessfullmektig = null,
                    ytelse = Ytelse.OMS_OMP,
                    type = type,
                    kildeReferanse = "duplicate-check-test",
                    fagsystem = Fagsystem.K9,
                    fagsakId = "synthetic-fagsak",
                    sakstype = Sakstype.FAGSAK,
                    arkivsaksnummer = null,
                    mottattKlageinstans = now,
                    frist = now.toLocalDate(),
                    feilregistrering = feilregistrering,
                    ferdigstilling = ferdigstilling,
                    previousSaksbehandlerident = "TEST",
                    gosysOppgaveId = null,
                    gosysOppgaveRequired = false,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                )
            }

            Type.ANKE_I_TRYGDERETTEN_ETTER_2027 -> {
                AnkeITrygderettenbehandlingEtter2027(
                    sendtTilTrygderetten = now,
                    trygderettenSaksnummer = saksnummer,
                    previousBehandlingId = null,
                    klager = klager,
                    sakenGjelder = sakenGjelder,
                    prosessfullmektig = null,
                    ytelse = Ytelse.OMS_OMP,
                    type = type,
                    kildeReferanse = "duplicate-check-test",
                    fagsystem = Fagsystem.K9,
                    fagsakId = "synthetic-fagsak",
                    sakstype = Sakstype.FAGSAK,
                    arkivsaksnummer = null,
                    mottattKlageinstans = now,
                    frist = now.toLocalDate(),
                    feilregistrering = feilregistrering,
                    ferdigstilling = ferdigstilling,
                    previousSaksbehandlerident = "TEST",
                    gosysOppgaveId = null,
                    gosysOppgaveRequired = false,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                )
            }

            Type.BEGJAERING_OM_GJENOPPTAK_ETTER_2027 -> {
                GjenopptaksbehandlingEtter2027(
                    trygderettenSaksnummer = saksnummer,
                    klageBehandlendeEnhet = "4219",
                    kakaKvalitetsvurderingId = null,
                    kakaKvalitetsvurderingVersion = 2,
                    varsletBehandlingstid = null,
                    forlengetBehandlingstidDraft = null,
                    previousBehandlingId = null,
                    klager = klager,
                    sakenGjelder = sakenGjelder,
                    prosessfullmektig = null,
                    ytelse = Ytelse.OMS_OMP,
                    type = type,
                    kildeReferanse = "duplicate-check-test",
                    fagsystem = Fagsystem.K9,
                    fagsakId = "synthetic-fagsak",
                    sakstype = Sakstype.FAGSAK,
                    arkivsaksnummer = null,
                    mottattKlageinstans = now,
                    frist = now.toLocalDate(),
                    feilregistrering = feilregistrering,
                    ferdigstilling = ferdigstilling,
                    previousSaksbehandlerident = "TEST",
                    gosysOppgaveId = null,
                    gosysOppgaveRequired = false,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                )
            }

            Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_ETTER_2027 -> {
                GjenopptakITrygderettenbehandlingEtter2027(
                    trygderettenSaksnummer = saksnummer,
                    sendtTilTrygderetten = now,
                    previousBehandlingId = null,
                    klager = klager,
                    sakenGjelder = sakenGjelder,
                    prosessfullmektig = null,
                    ytelse = Ytelse.OMS_OMP,
                    type = type,
                    kildeReferanse = "duplicate-check-test",
                    fagsystem = Fagsystem.K9,
                    fagsakId = "synthetic-fagsak",
                    sakstype = Sakstype.FAGSAK,
                    arkivsaksnummer = null,
                    mottattKlageinstans = now,
                    feilregistrering = feilregistrering,
                    ferdigstilling = ferdigstilling,
                    previousSaksbehandlerident = "TEST",
                    gosysOppgaveId = null,
                    gosysOppgaveRequired = false,
                    initiatingSystem = Behandling.InitiatingSystem.KABAL,
                )
            }

            else -> {
                error("Unsupported test type: $type")
            }
        }
    }

    private fun syntheticTrygderettenSaksnummer(): String = "2027-TEST-${UUID.randomUUID()}"

    private fun Behandling.addTildelingHistorikk(
        ident: String?,
        tidspunkt: LocalDateTime,
    ) {
        tildelingHistorikk.add(
            TildelingHistorikk(
                saksbehandlerident = ident,
                enhet = ident?.let { enhet1 },
                hjemmelIdList = null,
                tidspunkt = tidspunkt,
                fradelingReason = null,
                utfoerendeIdent = null,
                utfoerendeNavn = null,
            ),
        )
    }

    private fun persistDokument(
        behandling: Behandling,
        dokumentType: DokumentType,
        avsender: String? = null,
        created: LocalDateTime = LocalDateTime.now(),
    ) {
        val dokument =
            OpplastetDokumentUnderArbeidAsHoveddokument(
                mellomlagerId = UUID.randomUUID().toString(),
                mellomlagretDate = created,
                size = 1001,
                name = "Dokument.pdf",
                behandlingId = behandling.id,
                created = created,
                dokumentType = dokumentType,
                creatorIdent = "S123456",
                creatorRole = BehandlingRole.KABAL_SAKSBEHANDLING,
                datoMottatt = created.toLocalDate(),
                journalfoerendeEnhetId = null,
                inngaaendeKanal = null,
            )
        avsender?.let {
            dokument.brevmottakere.add(
                Brevmottaker(
                    technicalPartId = UUID.randomUUID(),
                    identifikator = it,
                    localPrint = false,
                    forceCentralPrint = false,
                    address = null,
                    navn = null,
                ),
            )
        }
        testEntityManager.persist(dokument)
    }

    companion object {
        private const val ORGNR_TRYGDERETTEN = "974761084"
        private val PAAANKET_VEDTAKSDATO: LocalDate = LocalDate.of(2020, 1, 1)

        @JvmStatic
        fun trygderettenTypes(): Stream<Type> =
            Stream.of(
                Type.ANKE_ETTER_2027,
                Type.ANKE_I_TRYGDERETTEN_ETTER_2027,
                Type.BEGJAERING_OM_GJENOPPTAK_ETTER_2027,
                Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_ETTER_2027,
            )

        @JvmStatic
        fun exactMatchNearMisses(): Stream<Arguments> {
            val saksnummer = "2027-TEST-${UUID.randomUUID()}"
            return Stream.of(
                Arguments.of(saksnummer, saksnummer.lowercase()),
                Arguments.of(saksnummer, " $saksnummer"),
                Arguments.of(saksnummer, "$saksnummer "),
                Arguments.of(saksnummer, "PREFIX-$saksnummer"),
            )
        }

        @JvmStatic
        fun behandlingProvider(): Stream<Behandling> =
            Stream.of(
                getKlagebehandling(),
                getAnkebehandling(),
                getAnkeITrygderettenbehandling(),
                getBehandlingEtterTrygderettenOpphevet(),
                getOmgjoeringskravbehandling(),
                getGjenopptaksbehandling(),
                getGjenopptakITrygderettenbehandling(),
            )

        @JvmStatic
        fun consolidatedBehandlingProvider(): Stream<Arguments> =
            Stream.of(
                Arguments.of(getOmgjoeringskravbehandling(), Omgjoeringskravbehandling::class.java),
                Arguments.of(getGjenopptaksbehandling(), GjenopptaksbehandlingFoer2027::class.java),
            )

        private fun getKlagebehandling(
            fagsakId: String? = "123",
            sakstype: Sakstype = Sakstype.FAGSAK,
            arkivsaksnummer: String? = null,
        ) = Klagebehandling(
            klager =
                Klager(
                    id = UUID.randomUUID(),
                    partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                ),
            sakenGjelder =
                SakenGjelder(
                    id = UUID.randomUUID(),
                    partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
            mottattKlageinstans = LocalDateTime.now(),
            fagsystem = Fagsystem.K9,
            fagsakId = fagsakId,
            sakstype = sakstype,
            arkivsaksnummer = arkivsaksnummer,
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

        private fun getAnkebehandling() =
            AnkebehandlingFoer2027(
                klager =
                    Klager(
                        id = UUID.randomUUID(),
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                    ),
                sakenGjelder =
                    SakenGjelder(
                        id = UUID.randomUUID(),
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                    ),
                prosessfullmektig = null,
                ytelse = Ytelse.OMS_OMP,
                type = Type.ANKE_FOER_2027,
                frist = LocalDate.now(),
                hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
                mottattKlageinstans = LocalDateTime.now(),
                fagsystem = Fagsystem.K9,
                fagsakId = "123",
                sakstype = Sakstype.FAGSAK,
                arkivsaksnummer = null,
                kildeReferanse = "abc",
                klageBehandlendeEnhet = "4219",
                kakaKvalitetsvurderingVersion = 2,
                kakaKvalitetsvurderingId = UUID.randomUUID(),
                paaanketVedtaksdato = PAAANKET_VEDTAKSDATO,
                forsterketRett = true,
                previousSaksbehandlerident = "C78901",
                gosysOppgaveId = null,
                varsletBehandlingstid = null,
                forlengetBehandlingstidDraft = null,
                gosysOppgaveRequired = false,
                initiatingSystem = Behandling.InitiatingSystem.KABAL,
                previousBehandlingId = null,
            )

        private fun getOmgjoeringskravbehandling() =
            Omgjoeringskravbehandling(
                klager =
                    Klager(
                        id = UUID.randomUUID(),
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                    ),
                sakenGjelder =
                    SakenGjelder(
                        id = UUID.randomUUID(),
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                    ),
                prosessfullmektig = null,
                ytelse = Ytelse.OMS_OMP,
                type = Type.OMGJOERINGSKRAV,
                frist = LocalDate.now(),
                hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
                mottattKlageinstans = LocalDateTime.now(),
                fagsystem = Fagsystem.K9,
                fagsakId = "123",
                sakstype = Sakstype.FAGSAK,
                arkivsaksnummer = null,
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

        private fun getAnkeITrygderettenbehandling() =
            AnkeITrygderettenbehandlingFoer2027(
                klager =
                    Klager(
                        id = UUID.randomUUID(),
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                    ),
                sakenGjelder =
                    SakenGjelder(
                        id = UUID.randomUUID(),
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                    ),
                prosessfullmektig = null,
                ytelse = Ytelse.OMS_OMP,
                type = Type.ANKE_I_TRYGDERETTEN_FOER_2027,
                frist = LocalDate.now(),
                hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
                mottattKlageinstans = LocalDateTime.now(),
                fagsystem = Fagsystem.K9,
                fagsakId = "123",
                sakstype = Sakstype.FAGSAK,
                arkivsaksnummer = null,
                kildeReferanse = "abc",
                sendtTilTrygderetten = LocalDateTime.now(),
                paaanketVedtaksdato = PAAANKET_VEDTAKSDATO,
                forsterketRett = true,
                previousSaksbehandlerident = "C78901",
                gosysOppgaveId = null,
                gosysOppgaveRequired = false,
                initiatingSystem = Behandling.InitiatingSystem.KABAL,
                previousBehandlingId = null,
            )

        private fun getGjenopptakITrygderettenbehandling() =
            GjenopptakITrygderettenbehandlingFoer2027(
                klager =
                    Klager(
                        id = UUID.randomUUID(),
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                    ),
                sakenGjelder =
                    SakenGjelder(
                        id = UUID.randomUUID(),
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                    ),
                prosessfullmektig = null,
                ytelse = Ytelse.OMS_OMP,
                type = Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027,
                frist = LocalDate.now(),
                hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
                mottattKlageinstans = LocalDateTime.now(),
                fagsystem = Fagsystem.K9,
                fagsakId = "123",
                sakstype = Sakstype.FAGSAK,
                arkivsaksnummer = null,
                kildeReferanse = "abc",
                sendtTilTrygderetten = LocalDateTime.now(),
                paaanketVedtaksdato = PAAANKET_VEDTAKSDATO,
                forsterketRett = true,
                previousSaksbehandlerident = "C78901",
                gosysOppgaveId = null,
                gosysOppgaveRequired = false,
                initiatingSystem = Behandling.InitiatingSystem.KABAL,
                previousBehandlingId = null,
            )

        private fun getBehandlingEtterTrygderettenOpphevet() =
            BehandlingEtterTrygderettenOpphevet(
                klager =
                    Klager(
                        id = UUID.randomUUID(),
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                    ),
                sakenGjelder =
                    SakenGjelder(
                        id = UUID.randomUUID(),
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                    ),
                prosessfullmektig = null,
                ytelse = Ytelse.OMS_OMP,
                type = Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET,
                frist = LocalDate.now(),
                hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
                mottattKlageinstans = LocalDateTime.now(),
                fagsystem = Fagsystem.K9,
                fagsakId = "123",
                sakstype = Sakstype.FAGSAK,
                arkivsaksnummer = null,
                kildeReferanse = "abc",
                kakaKvalitetsvurderingVersion = 2,
                kakaKvalitetsvurderingId = UUID.randomUUID(),
                varsletBehandlingstid = null,
                kjennelseMottatt = LocalDateTime.now(),
                ankeBehandlendeEnhet = "4219",
                forlengetBehandlingstidDraft = null,
                previousSaksbehandlerident = "C78901",
                gosysOppgaveId = null,
                gosysOppgaveRequired = false,
                initiatingSystem = Behandling.InitiatingSystem.KABAL,
                previousBehandlingId = null,
            )

        private fun getGjenopptaksbehandling() =
            GjenopptaksbehandlingFoer2027(
                klager =
                    Klager(
                        id = UUID.randomUUID(),
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                    ),
                sakenGjelder =
                    SakenGjelder(
                        id = UUID.randomUUID(),
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                    ),
                prosessfullmektig = null,
                ytelse = Ytelse.OMS_OMP,
                type = Type.BEGJAERING_OM_GJENOPPTAK_FOER_2027,
                frist = LocalDate.now(),
                hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
                mottattKlageinstans = LocalDateTime.now(),
                fagsystem = Fagsystem.K9,
                fagsakId = "123",
                sakstype = Sakstype.FAGSAK,
                arkivsaksnummer = null,
                kildeReferanse = "abc",
                klageBehandlendeEnhet = "4219",
                kakaKvalitetsvurderingId = UUID.randomUUID(),
                kakaKvalitetsvurderingVersion = 2,
                paaanketVedtaksdato = PAAANKET_VEDTAKSDATO,
                forsterketRett = true,
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
