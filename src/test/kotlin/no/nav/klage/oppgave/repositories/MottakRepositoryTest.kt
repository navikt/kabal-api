package no.nav.klage.oppgave.repositories

import com.ninjasquad.springmockk.MockkBean
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.domain.mottak.MottakDokument
import no.nav.klage.oppgave.domain.mottak.MottakDokumentType
import no.nav.klage.oppgave.domain.mottak.MottakHjemmel
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
class MottakRepositoryTest {

    companion object {
        @Container
        @JvmField
        val postgreSQLContainer: TestPostgresqlContainer = TestPostgresqlContainer.instance
    }

    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var mottakRepository: MottakRepository

    //Because of Hibernate Envers and our setup for audit logs.
    @MockkBean
    lateinit var tokenUtil: TokenUtil

    @Test
    fun `persist mottak works`() {
        val mottak = Mottak(
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "123454")
            ),
            fagsystem = Fagsystem.AO01,
            fagsakId = "12345",
            kildeReferanse = "54321",
            dvhReferanse = "5342523",
            hjemler = mutableSetOf(MottakHjemmel(hjemmelId = Hjemmel.FTRL_8_7.id)),
            forrigeSaksbehandlerident = "Z123456",
            forrigeBehandlendeEnhet = "1234",
            mottakDokument = mutableSetOf(
                MottakDokument(
                    type = MottakDokumentType.OVERSENDELSESBREV,
                    journalpostId = "245245"
                )
            ),
            sakMottattKaDato = LocalDateTime.now(),
            brukersKlageMottattVedtaksinstans = LocalDate.now(),
            kommentar = null,
            prosessfullmektig = null,
            sakenGjelder = null,
            frist = null,
            forrigeBehandlingId = null,
            sentFrom = Mottak.Sender.FAGSYSTEM,
        )

        mottakRepository.save(mottak)

        testEntityManager.flush()
        testEntityManager.clear()

        assertThat(mottakRepository.findById(mottak.id).get()).isEqualTo(mottak)

        assertThat(mottakRepository.findBySakenGjelderOrKlager("123454").first()).isEqualTo(mottak)
    }

}
