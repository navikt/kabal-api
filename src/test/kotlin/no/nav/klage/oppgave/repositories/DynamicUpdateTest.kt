package no.nav.klage.oppgave.repositories

import com.ninjasquad.springmockk.MockkBean
import jakarta.persistence.EntityManagerFactory
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.db.PostgresIntegrationTestBase
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

/**
 * This test verifies that @DynamicUpdate works correctly for Behandling subclasses.
 *
 * The problem: @DynamicUpdate is placed on the parent sealed class Behandling, but Hibernate
 * may not inherit this annotation to concrete subclass entities (like Klagebehandling).
 * Without @DynamicUpdate being effective, Hibernate generates UPDATE statements that include
 * ALL columns. This causes a "last write wins" problem when two transactions load the same
 * entity and modify different properties — the second transaction overwrites changes made
 * by the first transaction.
 *
 * The test simulates this scenario:
 * 1. Save a Klagebehandling
 * 2. Open two independent EntityManager sessions (simulating two concurrent transactions)
 * 3. Session 1 modifies property A (frist), flushes and commits
 * 4. Session 2 (loaded from same snapshot) modifies property B (utfall), flushes and commits
 * 5. Assert that BOTH changes are present in the database
 *
 * If @DynamicUpdate is NOT effective, session 2's full UPDATE will overwrite the frist change
 * from session 1 back to its original value.
 */
@ActiveProfiles("local")
@DataJpaTest
class DynamicUpdateTest : PostgresIntegrationTestBase() {

    @Autowired
    lateinit var entityManagerFactory: EntityManagerFactory

    @MockkBean
    lateinit var tokenUtil: TokenUtil

    @Test
    fun `DynamicUpdate should prevent second transaction from overwriting changes made by first transaction`() {
        // Step 1: Create and persist a Klagebehandling with known initial values in its own committed transaction
        val originalFrist = LocalDate.of(2025, 1, 1)
        val klage = createKlagebehandling(frist = originalFrist)
        val behandlingId = klage.id

        val emSetup = entityManagerFactory.createEntityManager()
        emSetup.transaction.begin()
        emSetup.persist(klage)
        emSetup.transaction.commit()
        emSetup.close()

        // Step 2: Open two independent EntityManagers to simulate two concurrent transactions
        val em1 = entityManagerFactory.createEntityManager()
        val em2 = entityManagerFactory.createEntityManager()

        try {
            // Both transactions load the entity from the same database state
            val tx1 = em1.transaction
            tx1.begin()
            val behandling1 = em1.find(Klagebehandling::class.java, behandlingId)

            val tx2 = em2.transaction
            tx2.begin()
            val behandling2 = em2.find(Klagebehandling::class.java, behandlingId)

            // Verify both loaded the same initial state
            assertThat(behandling1.frist).isEqualTo(originalFrist)
            assertThat(behandling2.frist).isEqualTo(originalFrist)
            assertThat(behandling1.utfall).isNull()
            assertThat(behandling2.utfall).isNull()

            // Step 3: Transaction 1 modifies frist and commits
            val newFrist = LocalDate.of(2026, 6, 15)
            behandling1.frist = newFrist
            em1.flush()
            tx1.commit()

            // Step 4: Transaction 2 modifies utfall and commits
            // (it still has the old snapshot where frist = originalFrist)
            behandling2.utfall = Utfall.STADFESTELSE
            em2.flush()
            tx2.commit()

            // Step 5: Load fresh from database and verify BOTH changes are present
            val emVerify = entityManagerFactory.createEntityManager()
            val result = emVerify.find(Klagebehandling::class.java, behandlingId)
            emVerify.close()

            // If @DynamicUpdate works correctly on subclasses:
            //   - frist should be newFrist (from transaction 1)
            //   - utfall should be STADFESTELSE (from transaction 2)
            //
            // If @DynamicUpdate does NOT work on subclasses:
            //   - frist will be originalFrist (overwritten by transaction 2's full UPDATE)
            //   - utfall will be STADFESTELSE
            assertThat(result.utfall)
                .describedAs("utfall should be set by transaction 2")
                .isEqualTo(Utfall.STADFESTELSE)

            assertThat(result.frist)
                .describedAs(
                    "frist should still be the value set by transaction 1. " +
                            "If this fails, @DynamicUpdate is NOT effective on subclasses, " +
                            "and transaction 2's full UPDATE overwrote the frist change."
                )
                .isEqualTo(newFrist)

        } finally {
            if (em1.isOpen) em1.close()
            if (em2.isOpen) em2.close()
        }
    }

    @Test
    fun `DynamicUpdate should work for multiple different properties modified in separate transactions`() {
        // Test with more properties to make the issue even clearer
        val originalFrist = LocalDate.of(2025, 1, 1)
        val klage = createKlagebehandling(frist = originalFrist)
        val behandlingId = klage.id

        val emSetup = entityManagerFactory.createEntityManager()
        emSetup.transaction.begin()
        emSetup.persist(klage)
        emSetup.transaction.commit()
        emSetup.close()

        val em1 = entityManagerFactory.createEntityManager()
        val em2 = entityManagerFactory.createEntityManager()

        try {
            // Both transactions load the same snapshot
            val tx1 = em1.transaction
            tx1.begin()
            val behandling1 = em1.find(Klagebehandling::class.java, behandlingId)

            val tx2 = em2.transaction
            tx2.begin()
            val behandling2 = em2.find(Klagebehandling::class.java, behandlingId)

            // Transaction 1: modify rolIdent
            behandling1.rolIdent = "SAKSBEHANDLER_X"
            em1.flush()
            tx1.commit()

            // Transaction 2: modify utfall (still has old snapshot where rolIdent is null)
            behandling2.utfall = Utfall.MEDHOLD
            em2.flush()
            tx2.commit()

            // Verify both changes survived
            val emVerify = entityManagerFactory.createEntityManager()
            val result = emVerify.find(Klagebehandling::class.java, behandlingId)
            emVerify.close()

            assertThat(result.utfall)
                .describedAs("utfall should be set by transaction 2")
                .isEqualTo(Utfall.MEDHOLD)

            assertThat(result.rolIdent)
                .describedAs(
                    "rolIdent should still be the value set by transaction 1. " +
                            "If this is null, @DynamicUpdate is NOT effective on subclasses."
                )
                .isEqualTo("SAKSBEHANDLER_X")

        } finally {
            if (em1.isOpen) em1.close()
            if (em2.isOpen) em2.close()
        }
    }

    private fun createKlagebehandling(frist: LocalDate = LocalDate.now()): Klagebehandling = Klagebehandling(
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
        frist = frist,
        hjemler = mutableSetOf(Hjemmel.FTRL_8_7),
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
}
