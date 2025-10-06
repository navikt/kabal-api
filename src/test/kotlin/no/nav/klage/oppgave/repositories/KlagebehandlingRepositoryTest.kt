package no.nav.klage.oppgave.repositories

import com.ninjasquad.springmockk.MockkBean
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.behandling.Ankebehandling
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Ferdigstilling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.behandling.subentities.Saksdokument
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@ActiveProfiles("local")
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class KlagebehandlingRepositoryTest {

    companion object {
        @Container
        @JvmField
        val postgreSQLContainer: TestPostgresqlContainer = TestPostgresqlContainer.instance
    }

    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var klagebehandlingRepository: KlagebehandlingRepository

    @Autowired
    lateinit var ankebehandlingRepository: AnkebehandlingRepository

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    //Because of Hibernate Envers and our setup for audit logs.
    @MockkBean
    lateinit var tokenUtil: TokenUtil

    @Test
    fun `persist klage works`() {
        val klage = getKlagebehandling()
        klagebehandlingRepository.save(klage)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(klagebehandlingRepository.findById(klage.id).get()).isEqualTo(klage)
    }

    @Test
    fun `persist klage with saksdokumenter works`() {
        val klagebehandling = getKlagebehandling(
            saksdokumenter = mutableSetOf(
                Saksdokument(journalpostId = "REF1", dokumentInfoId = "123"),
                Saksdokument(journalpostId = "REF2", dokumentInfoId = "321"),
            )
        )

        klagebehandlingRepository.save(klagebehandling)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(klagebehandlingRepository.findById(klagebehandling.id).get()).isEqualTo(klagebehandling)
    }

    @Test
    fun `remove saksdokument on saved klage works`() {
        val klagebehandling = getKlagebehandling(
            saksdokumenter = mutableSetOf(
                Saksdokument(journalpostId = "REF1", dokumentInfoId = "123"),
                Saksdokument(journalpostId = "REF2", dokumentInfoId = "321"),
            )
        )

        klagebehandlingRepository.save(klagebehandling)

        testEntityManager.flush()
        testEntityManager.clear()

        val foundklage = klagebehandlingRepository.findById(klagebehandling.id).get()
        foundklage.saksdokumenter.removeIf { it.journalpostId == "REF1" }

        testEntityManager.flush()
        testEntityManager.clear()

        val foundModifiedKlage = klagebehandlingRepository.findById(klagebehandling.id).get()
        assertThat(foundModifiedKlage.saksdokumenter).hasSize(1)
        assertThat(foundModifiedKlage.saksdokumenter.first().journalpostId).isEqualTo("REF2")
    }

    @Test
    fun `get ankemuligheter returns all three instances`() {
        val klageWithNoAnke = getKlagebehandling()
        klageWithNoAnke.ferdigstilling = Ferdigstilling(
            avsluttet = LocalDateTime.now(),
            avsluttetAvSaksbehandler = LocalDateTime.now(),
            navIdent = "navIdent",
            navn = "navn",
        )
        klageWithNoAnke.utfall = Utfall.STADFESTELSE

        val klageWithNoAnke2 = getKlagebehandling()
        klageWithNoAnke2.ferdigstilling = Ferdigstilling(
            avsluttet = LocalDateTime.now(),
            avsluttetAvSaksbehandler = LocalDateTime.now(),
            navIdent = "navIdent",
            navn = "navn",
        )

        klageWithNoAnke2.utfall = Utfall.RETUR

        val klageWithAnke = getKlagebehandling()
        klageWithAnke.ferdigstilling = Ferdigstilling(
            avsluttet = LocalDateTime.now(),
            avsluttetAvSaksbehandler = LocalDateTime.now(),
            navIdent = "navIdent",
            navn = "navn",
        )
        klageWithAnke.utfall = Utfall.STADFESTELSE

        klagebehandlingRepository.saveAll(listOf(klageWithNoAnke, klageWithNoAnke2, klageWithAnke))

        val ankebehandling = Ankebehandling(
            klageBehandlendeEnhet = "",
            sourceBehandlingId = klageWithAnke.id,
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
            kildeReferanse = "abc",
            dvhReferanse = "abc",
            fagsystem = Fagsystem.K9,
            fagsakId = "123",
            mottattKlageinstans = LocalDateTime.now(),
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 2,
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            frist = LocalDate.now().plusWeeks(12),
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
        )

        ankebehandlingRepository.save(ankebehandling)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(behandlingRepository.getAnkemuligheter("23452354")).containsExactlyInAnyOrder(
            klageWithNoAnke,
            klageWithNoAnke2,
            klageWithAnke
        )
    }

    fun getKlagebehandling(
        saksdokumenter: MutableSet<Saksdokument>? = null,
    ): Klagebehandling = Klagebehandling(
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
        hjemler = mutableSetOf(
            Hjemmel.FTRL_8_7
        ),
        created = LocalDateTime.now(),
        modified = LocalDateTime.now(),
        mottattKlageinstans = LocalDateTime.now(),
        fagsystem = Fagsystem.K9,
        fagsakId = "123",
        kildeReferanse = "abc",
        avsenderEnhetFoersteinstans = "0101",
        mottattVedtaksinstans = LocalDate.now(),
        saksdokumenter = saksdokumenter ?: mutableSetOf(),
        kakaKvalitetsvurderingId = UUID.randomUUID(),
        kakaKvalitetsvurderingVersion = 2,
        previousSaksbehandlerident = "C78901",
        gosysOppgaveId = null,
        varsletBehandlingstid = null,
        forlengetBehandlingstidDraft = null,
        gosysOppgaveRequired = false,
        initiatingSystem = Behandling.InitiatingSystem.KABAL,
    )
}