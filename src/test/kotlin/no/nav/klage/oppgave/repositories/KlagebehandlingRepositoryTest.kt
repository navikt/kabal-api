package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.klage.*
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
    lateinit var mottakRepository: MottakRepository

    @Test
    fun `persist klage works`() {

        val mottak = getMottak()
        mottakRepository.save(mottak)

        val klage = getKlagebehandling(mottak.id)
        klagebehandlingRepository.save(klage)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(klagebehandlingRepository.findById(klage.id).get()).isEqualTo(klage)
    }

    @Test
    fun `persist klage with saksdokumenter works`() {

        val mottak = getMottak()
        mottakRepository.save(mottak)

        val klagebehandling = getKlagebehandling(
            mottakId = mottak.id,
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
        testEntityManager.flush()
        testEntityManager.clear()

        val mottak = getMottak()
        mottakRepository.save(mottak)
        val klagebehandling = getKlagebehandling(
            mottakId = mottak.id,
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
    fun `get ankemuligheter returns only one with no existing anke`() {
        val mottak1 = getMottak()
        val mottak2 = getMottak()
        val mottak3 = getMottak()

        mottakRepository.saveAll(listOf(mottak1, mottak2, mottak3))

        val klageWithNoAnke = getKlagebehandling(
            mottakId = mottak1.id,
        )
        klageWithNoAnke.avsluttet = LocalDateTime.now()
        klageWithNoAnke.utfall = Utfall.STADFESTELSE
        klageWithNoAnke.extraUtfallSet = setOf(Utfall.STADFESTELSE)

        val klageWithNoAnkeButNoAnkemulighet = getKlagebehandling(
            mottakId = mottak1.id
        )
        klageWithNoAnkeButNoAnkemulighet.avsluttet = LocalDateTime.now()
        klageWithNoAnkeButNoAnkemulighet.utfall = Utfall.RETUR
        klageWithNoAnkeButNoAnkemulighet.extraUtfallSet = setOf(Utfall.RETUR)

        val klageWithAnke = getKlagebehandling(
            mottakId = mottak2.id
        )
        klageWithAnke.avsluttet = LocalDateTime.now()
        klageWithAnke.utfall = Utfall.STADFESTELSE
        klageWithAnke.extraUtfallSet = setOf(Utfall.STADFESTELSE)

        klagebehandlingRepository.saveAll(listOf(klageWithNoAnke, klageWithNoAnkeButNoAnkemulighet, klageWithAnke))

        val ankebehandling = Ankebehandling(
            klageBehandlendeEnhet = "",
            klagebehandlingId = klageWithAnke.id,
            klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
            sakenGjelder = SakenGjelder(
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                skalMottaKopi = false
            ),
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
        )

        ankebehandlingRepository.save(ankebehandling)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(klagebehandlingRepository.getAnkemuligheter("23452354")).containsExactly(klageWithNoAnke)
    }


    fun getMottak(): Mottak = Mottak(
        ytelse = Ytelse.OMS_OMP,
        type = Type.KLAGE,
        klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
        kildeReferanse = "1234234",
        sakMottattKaDato = LocalDateTime.now(),
        fagsystem = Fagsystem.K9,
        fagsakId = "123",
        forrigeBehandlendeEnhet = "0101",
        brukersHenvendelseMottattNavDato = LocalDate.now(),
        kommentar = null,
    )

    fun getKlagebehandling(
        mottakId: UUID,
        saksdokumenter: MutableSet<Saksdokument>? = null,
    ): Klagebehandling = Klagebehandling(
        klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
        sakenGjelder = SakenGjelder(
            partId = PartId(type = PartIdType.PERSON, value = "23452354"),
            skalMottaKopi = false
        ),
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
        mottakId = mottakId,
        avsenderEnhetFoersteinstans = "0101",
        mottattVedtaksinstans = LocalDate.now(),
        saksdokumenter = saksdokumenter ?: mutableSetOf(),
        kakaKvalitetsvurderingId = UUID.randomUUID(),
        kakaKvalitetsvurderingVersion = 2,
    )
}