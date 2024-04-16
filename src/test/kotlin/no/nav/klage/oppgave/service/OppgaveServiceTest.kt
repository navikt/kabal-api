package no.nav.klage.oppgave.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.view.MineFerdigstilteOppgaverQueryParams
import no.nav.klage.oppgave.api.view.Rekkefoelge
import no.nav.klage.oppgave.api.view.Sortering
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.MottakRepository
import org.junit.jupiter.api.BeforeEach
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
class OppgaveServiceTest {

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

    @MockkBean
    lateinit var innloggetSaksbehandlerService: InnloggetSaksbehandlerService

    lateinit var oppgaveService: OppgaveService

    private val SAKSBEHANDLER_IDENT = "SAKSBEHANDLER_IDENT"

    lateinit var behandlingId: UUID

    @BeforeEach
    fun setup() {
        val behandling = simpleInsert()
        behandlingId = behandling.id
        oppgaveService = OppgaveService(
            behandlingRepository = behandlingRepository,
            innloggetSaksbehandlerService = innloggetSaksbehandlerService,
        )
        every { innloggetSaksbehandlerService.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
    }

    @Test
    fun `Forsøk på ferdigstilling av behandling som allerede er avsluttet av saksbehandler skal ikke lykkes`() {
        val behandling = simpleInsert(fullfoert = true)

        val results = oppgaveService.getFerdigstilteOppgaverForNavIdent(
            MineFerdigstilteOppgaverQueryParams(
                typer = listOf(Type.KLAGE.id),
                ytelser = emptyList(),
                registreringshjemler = emptyList(),
                rekkefoelge = Rekkefoelge.STIGENDE,
                sortering = Sortering.AVSLUTTET_AV_SAKSBEHANDLER,
                ferdigstiltFrom = LocalDate.now().minusDays(1),
                ferdigstiltTo = LocalDate.now().plusDays(1),
            )
        )

    }

    private fun simpleInsert(
        fullfoert: Boolean = false,
        utfall: Boolean = true,
        hjemler: Boolean = true,
        trukket: Boolean = false
    ): Behandling {
        val mottak = Mottak(
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

        mottakRepository.save(mottak)

        val now = LocalDateTime.now()

        val behandling = Klagebehandling(
            klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
            sakenGjelder = SakenGjelder(
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                skalMottaKopi = false
            ),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            frist = LocalDate.now(),
            hjemler = if (hjemler) mutableSetOf(
                Hjemmel.FTRL_8_7
            ) else mutableSetOf(),
            created = now,
            modified = now,
            mottattKlageinstans = now,
            fagsystem = Fagsystem.K9,
            fagsakId = "123",
            kildeReferanse = "abc",
            mottakId = mottak.id,
            mottattVedtaksinstans = LocalDate.now(),
            avsenderEnhetFoersteinstans = "enhet",
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 2,
            utfall = when {
                trukket -> Utfall.TRUKKET
                utfall -> Utfall.AVVIST
                else -> null
            },
            extraUtfallSet = when {
                trukket -> setOf(Utfall.TRUKKET)
                utfall -> setOf(Utfall.AVVIST)
                else -> emptySet()
            },
            registreringshjemler = if (hjemler) mutableSetOf(
                Registreringshjemmel.ANDRE_TRYGDEAVTALER
            ) else mutableSetOf(),
            avsluttetAvSaksbehandler = if (fullfoert) LocalDateTime.now() else null,
            previousSaksbehandlerident = "C78901",
        )

        behandlingRepository.save(behandling)

        testEntityManager.flush()
        testEntityManager.clear()

        return behandling
    }
}
