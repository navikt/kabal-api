package no.nav.klage.oppgave.service

import com.ninjasquad.springmockk.MockkBean
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.saksbehandler.Enhet
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerPersonligInfo
import no.nav.klage.oppgave.eventlisteners.CreateBehandlingFromMottakEventListener
import no.nav.klage.oppgave.exceptions.DuplicateOversendelseException
import no.nav.klage.oppgave.gateway.AzureGateway
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
    lateinit var createBehandlingFromMottakEventListener: CreateBehandlingFromMottakEventListener

    @MockkBean(relaxed = true)
    lateinit var norg2Client: Norg2Client

    @MockkBean(relaxed = true)
    lateinit var azureGateway: AzureGateway

    @MockkBean(relaxed = true)
    lateinit var meterReqistry: MeterRegistry

    @MockkBean(relaxed = true)
    lateinit var pdlFacade: PdlFacade

    @MockkBean(relaxed = true)
    lateinit var eregClient: EregClient

    @Autowired
    lateinit var mottakService: MottakService

    @Test
    fun `duplicate oversendelse throws exception`() {
        val saksbehandler = "Z123456"
        every {
            azureGateway.getPersonligDataOmSaksbehandlerMedIdent(saksbehandler)
        } returns SaksbehandlerPersonligInfo(
            navIdent = saksbehandler,
            azureId = "Whatever",
            fornavn = "Test",
            etternavn = "Saksbehandler",
            sammensattNavn = "Test Saksbehandler",
            epost = "test.saksbehandler@trygdeetaten.no",
            enhet = Enhet("4295", "KA Nord")
        )

        every { pdlFacade.personExists(any()) } returns true

        val oversendtKlage = OversendtKlageV2(
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

        mottakService.createMottakForKlageV2(oversendtKlage)

        assertThrows<DuplicateOversendelseException> { mottakService.createMottakForKlageV2(oversendtKlage) }
    }
}