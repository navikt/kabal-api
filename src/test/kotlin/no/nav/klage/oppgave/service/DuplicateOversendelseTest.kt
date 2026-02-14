package no.nav.klage.oppgave.service

import com.ninjasquad.springmockk.MockkBean
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.db.PostgresIntegrationTestBase
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerEnhet
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerPersonligInfo
import no.nav.klage.oppgave.exceptions.DuplicateOversendelseException
import no.nav.klage.oppgave.util.TokenUtil
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.AutoConfigureDataJpa
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate

@ActiveProfiles("local")
@SpringBootTest(classes = [MottakService::class])
@EnableJpaRepositories(basePackages = ["no.nav.klage.oppgave.repositories", "no.nav.klage.dokument.repositories"])
@EntityScan("no.nav.klage.oppgave.domain", "no.nav.klage.dokument.domain")
@AutoConfigureDataJpa
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class DuplicateOversendelseTest : PostgresIntegrationTestBase() {

    @MockkBean(relaxed = true)
    lateinit var dokumentService: DokumentService

    @MockkBean(relaxed = true)
    lateinit var createBehandlingFromMottak: CreateBehandlingFromMottak

    @MockkBean(relaxed = true)
    lateinit var norg2Client: Norg2Client

    @MockkBean(relaxed = true)
    lateinit var klageLookupGateway: KlageLookupGateway

    @MockkBean(relaxed = true)
    lateinit var meterReqistry: MeterRegistry

    @MockkBean(relaxed = true)
    lateinit var personService: PersonService

    @MockkBean(relaxed = true)
    lateinit var eregClient: EregClient

    @MockkBean(relaxed = true)
    lateinit var dokumentUnderArbeidService: DokumentUnderArbeidService

    @MockkBean(relaxed = true)
    lateinit var svarbrevSettingsService: SvarbrevSettingsService

    @MockkBean(relaxed = true)
    lateinit var behandlingService: BehandlingService

    @Autowired
    lateinit var mottakService: MottakService

    //Because of Hibernate Envers and our setup for audit logs.
    @MockkBean
    lateinit var tokenUtil: TokenUtil

    @Test
    @Disabled
    fun `duplicate oversendelse throws exception`() {
        val saksbehandler = "Z123456"
        every {
            klageLookupGateway.getUserInfoForGivenNavIdent(navIdent = saksbehandler)
        } returns SaksbehandlerPersonligInfo(
            navIdent = saksbehandler,
            fornavn = "Test",
            etternavn = "Saksbehandler",
            sammensattNavn = "Test Saksbehandler",
            epost = "test.saksbehandler@trygdeetaten.no",
            enhet = SaksbehandlerEnhet("4295", "KA Nord")
        )

        every { personService.personExists(any()) } returns true

        val oversendtKlage = OversendtKlageV2(
            avsenderEnhet = "4455",
            avsenderSaksbehandlerIdent = saksbehandler,
            innsendtTilNav = LocalDate.now(),
            mottattFoersteinstans = LocalDate.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            klager = OversendtKlagerLegacy(
                id = OversendtPartId(
                    type = OversendtPartIdType.PERSON,
                    verdi = "01043137677"
                )
            ),
            kilde = Fagsystem.K9,
            kildeReferanse = "abc",
            fagsak = OversendtSak(
                fagsakId = "123",
                fagsystem = Fagsystem.K9
            ),
            hindreAutomatiskSvarbrev = null,
        )

        mottakService.createMottakForKlageV2(oversendtKlage)

        assertThrows<DuplicateOversendelseException> { mottakService.createMottakForKlageV2(oversendtKlage) }
    }
}