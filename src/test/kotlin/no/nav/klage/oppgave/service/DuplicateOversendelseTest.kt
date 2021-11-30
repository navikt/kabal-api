package no.nav.klage.oppgave.service

import com.ninjasquad.springmockk.MockkBean
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.exceptions.DuplicateOversendelseException
import no.nav.klage.oppgave.repositories.EnhetRepository
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureDataJpa
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate


@ActiveProfiles("local")
@SpringBootTest(classes = [MottakService::class])
@EnableJpaRepositories(basePackages = ["no.nav.klage.oppgave.repositories"])
@EntityScan("no.nav.klage.oppgave.domain")
@AutoConfigureDataJpa
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class DuplicateOversendelseTest {

    companion object {
        @Container
        @JvmField
        val postgreSQLContainer: TestPostgresqlContainer = TestPostgresqlContainer.instance
    }

    @MockkBean(relaxed = true)
    lateinit var dokumentService: DokumentService

    @MockkBean(relaxed = true)
    lateinit var norg2Client: Norg2Client

    @MockkBean(relaxed = true)
    lateinit var enhetRepository: EnhetRepository

    @MockkBean(relaxed = true)
    lateinit var meterReqistry: MeterRegistry

    @Autowired
    lateinit var mottakService: MottakService

    @Test
    fun `duplicate oversendelse throws exception`() {
        val saksbehandler = "Z123456"
        every { enhetRepository.getAnsatteIEnhet(any()) } returns listOf(saksbehandler)

        val oversendtKlage = OversendtKlage(
            avsenderEnhet = "4455",
            avsenderSaksbehandlerIdent = saksbehandler,
            innsendtTilNav = LocalDate.now(),
            mottattFoersteinstans = LocalDate.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            klager = OversendtKlager(
                id = OversendtPartId(
                    type = OversendtPartIdType.PERSON,
                    verdi = "01043137677"
                )
            ),
            kilde = KildeFagsystem.K9,
            kildeReferanse = "abc"
        )

        mottakService.createMottakForKlage(oversendtKlage)

        assertThrows<DuplicateOversendelseException> { mottakService.createMottakForKlage(oversendtKlage) }
    }
}