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
import java.util.*

@ActiveProfiles("local")
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BehandlingRepositoryTest {

    companion object {
        @Container
        @JvmField
        val postgreSQLContainer: TestPostgresqlContainer = TestPostgresqlContainer.instance
    }

    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @Autowired
    lateinit var mottakRepository: MottakRepository

    private val ENHET_1 = "ENHET_1"
    private val ENHET_2 = "ENHET_2"

    @Test
    fun `store Klagebehandling works`() {

        val mottak = getMottak()
        mottakRepository.save(mottak)

        val klage = getKlagebehandling(mottak.id)

        behandlingRepository.save(klage)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(behandlingRepository.findById(klage.id).get()).isEqualTo(klage)
    }

    @Test
    fun `store Klagebehandling with feilregistrering works`() {

        val mottak = getMottak()
        mottakRepository.save(mottak)

        val klagebehandling = getKlagebehandling(mottak.id)

        klagebehandling.feilregistrering = Feilregistrering(
            navIdent = "navIdent",
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

        val mottak1 = getMottak()
        val mottak2 = getMottak()
        val mottak3 = getMottak()
        val mottak4 = getMottak()
        mottakRepository.saveAll(listOf(mottak1, mottak2, mottak3, mottak4))

        val klageTildeltEnhet1 = getKlagebehandling(mottak1.id)
        klageTildeltEnhet1.tildeling = Tildeling(
            saksbehandlerident = "1", enhet = ENHET_1, tidspunkt = LocalDateTime.now()
        )

        val klageTildeltEnhet2 = getKlagebehandling(mottak2.id)
        klageTildeltEnhet2.tildeling = Tildeling(
            saksbehandlerident = "1", enhet = ENHET_2, tidspunkt = LocalDateTime.now()
        )

        val klageUtenTildeling = getKlagebehandling(mottak3.id)
        val fullfoertKlage = getKlagebehandling(mottak4.id)
        fullfoertKlage.tildeling = Tildeling(
            saksbehandlerident = "1", enhet = ENHET_1, tidspunkt = LocalDateTime.now()
        )
        fullfoertKlage.avsluttetAvSaksbehandler = LocalDateTime.now()

        behandlingRepository.saveAll(listOf(klageTildeltEnhet1, klageTildeltEnhet2, klageUtenTildeling, fullfoertKlage))

        testEntityManager.flush()
        testEntityManager.clear()
        val result =
            behandlingRepository.findByTildelingEnhetAndAvsluttetAvSaksbehandlerIsNullAndFeilregistreringIsNull(enhet = ENHET_1)

        assertThat(result).isEqualTo(listOf(klageTildeltEnhet1))
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

    fun getKlagebehandling(mottakId: UUID) = Klagebehandling(
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
        kakaKvalitetsvurderingVersion = 2,
        kakaKvalitetsvurderingId = UUID.randomUUID(),
        previousSaksbehandlerident = "C78901",
        oppgaveId = null,
    )

}
