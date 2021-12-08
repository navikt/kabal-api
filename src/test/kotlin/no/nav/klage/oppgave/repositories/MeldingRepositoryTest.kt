package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
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
import java.time.LocalTime

@ActiveProfiles("local")
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MeldingRepositoryTest {

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
    lateinit var meldingRepository: MeldingRepository

    @Autowired
    lateinit var mottakRepository: MottakRepository

    @Test
    fun `add meldinger works`() {

        val mottak = Mottak(
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
            kildeReferanse = "1234234",
            oversendtKaDato = LocalDateTime.now(),
            kildesystem = Fagsystem.K9,
            avsenderEnhet = "0101",
            mottattNavDato = LocalDate.now()
        )

        mottakRepository.save(mottak)

        val klage = Klagebehandling(
            klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
            sakenGjelder = SakenGjelder(
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                skalMottaKopi = false
            ),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(
                Hjemmel.FTL_8_7
            ),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            kildesystem = Fagsystem.K9,
            kildeReferanse = "abc",
            mottakId = mottak.id,
            avsenderEnhetFoersteinstans = "0101",
            mottattFoersteinstans = LocalDate.now(),
            vedtak = Vedtak(),
        )

        klagebehandlingRepository.save(klage)

        testEntityManager.flush()
        testEntityManager.clear()

        val foundKlagebehandling = klagebehandlingRepository.findById(klage.id).get()
        assertThat(foundKlagebehandling).isEqualTo(klage)

        val meldingTil1 = "min melding 1"
        val meldingTil2 = "min melding 2"

        val melding1 = Melding(
            text = meldingTil1,
            saksbehandlerident = "abc123",
            created = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 10)),
            klagebehandlingId = foundKlagebehandling.id
        )
        val melding2 = Melding(
            text = meldingTil2,
            saksbehandlerident = "abc456",
            created = LocalDateTime.of(LocalDate.now(), LocalTime.of(20, 20)),
            klagebehandlingId = foundKlagebehandling.id
        )

        meldingRepository.save(melding1)
        meldingRepository.save(melding2)

        testEntityManager.flush()
        testEntityManager.clear()

        val meldinger = meldingRepository.findByKlagebehandlingIdOrderByCreatedDesc(foundKlagebehandling.id)

        //latest first
        assertThat(meldinger.first().text).isEqualTo(meldingTil2)
    }

}
