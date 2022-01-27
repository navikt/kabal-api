package no.nav.klage.oppgave.service

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.exceptions.BehandlingManglerMedunderskriverException
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.repositories.MottakRepository
import no.nav.klage.oppgave.repositories.SaksbehandlerRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.ApplicationEventPublisher
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
class BehandlingServiceTest {

    companion object {
        @Container
        @JvmField
        val postgreSQLContainer: TestPostgresqlContainer = TestPostgresqlContainer.instance
    }

    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var mottakRepository: MottakRepository

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @SpykBean
    lateinit var tilgangService: TilgangService

    @MockkBean
    lateinit var innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository

    @MockkBean(relaxed = true)
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockkBean
    lateinit var pdlFacade: PdlFacade

    @MockkBean
    lateinit var egenAnsattService: EgenAnsattService

    @MockkBean
    lateinit var saksbehandlerRepository: SaksbehandlerRepository

    @MockkBean
    lateinit var kakaApiGateway: KakaApiGateway

    @MockkBean
    lateinit var dokumentService: DokumentService

    lateinit var behandlingService: BehandlingService

    private val SAKSBEHANDLER_IDENT = "SAKSBEHANDLER_IDENT"
    private val MEDUNDERSKRIVER_IDENT = "MEDUNDERSKRIVER_IDENT"
    private val DOKUMENTENHET_ID = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        behandlingService = BehandlingService(
            behandlingRepository,
            tilgangService,
            applicationEventPublisher,
            kakaApiGateway,
            dokumentService
        )
    }

    @Nested
    inner class GetBehandlingForUpdate {
        @Test
        fun `getBehandlingForUpdate ok`() {
            val behandling = simpleInsert()

            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTil(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilYtelse(any()) } returns Unit

            assertThat(
                behandlingService.getBehandlingForUpdate(
                    behandlingId = behandling.id,
                    ignoreCheckSkrivetilgang = true
                )
            ).isEqualTo(behandling)
        }

        @Test
        fun `getBehandlingForUpdate sjekker skrivetilgang, fanger riktig exception`() {
            val behandling = simpleInsert()

            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTil(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilYtelse(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling) }.throws(
                BehandlingAvsluttetException("")
            )

            assertThrows<BehandlingAvsluttetException> { behandlingService.getBehandlingForUpdate(behandling.id) }
        }
    }

    @Nested
    inner class SetMedunderskriverIdent {
        @Test
        fun `setMedunderskriverIdent kan sette medunderskriver til null`() {
            val behandling = simpleInsert()
            val behandlingId = behandling.id

            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTil(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilYtelse(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling) } returns Unit


            behandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                behandlingId,
                MEDUNDERSKRIVER_IDENT,
                SAKSBEHANDLER_IDENT
            )

            val result = behandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                behandlingId,
                null,
                SAKSBEHANDLER_IDENT
            )

            assertThat(result.currentDelbehandling().medunderskriver?.saksbehandlerident).isNull()
            assertThat(result.currentDelbehandling().medunderskriverHistorikk).hasSize(1)
        }
    }

    @Nested
    inner class SwitchMedunderskriverFlyt {
        @Test
        fun `switchMedunderskriverFlyt gir forventet feil når bruker er saksbehandler og medunderskriver ikke er satt`() {
            val behandling = simpleInsert()
            val behandlingId = behandling.id

            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilYtelse(any()) } returns Unit

            assertThrows<BehandlingManglerMedunderskriverException> {
                behandlingService.switchMedunderskriverFlyt(
                    behandlingId,
                    SAKSBEHANDLER_IDENT
                )
            }
        }

        @Test
        fun `switchMedunderskriverFlyt gir forventet status når bruker er saksbehandler og medunderskriver er satt`() {
            val behandling = simpleInsert()
            val behandlingId = behandling.id

            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilYtelse(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling) } returns Unit

            behandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                behandlingId,
                MEDUNDERSKRIVER_IDENT,
                SAKSBEHANDLER_IDENT
            )

            val result = behandlingService.switchMedunderskriverFlyt(
                behandlingId,
                SAKSBEHANDLER_IDENT
            )

            assertThat(result.currentDelbehandling().medunderskriverFlyt).isEqualTo(MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER)
        }

        @Test
        fun `switchMedunderskriverFlyt gir forventet status når bruker er medunderskriver`() {
            val behandling = simpleInsert()
            val behandlingId = behandling.id

            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns MEDUNDERSKRIVER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilYtelse(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling) } returns Unit

            behandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                behandlingId,
                MEDUNDERSKRIVER_IDENT,
                SAKSBEHANDLER_IDENT,
                MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER
            )

            val result = behandlingService.switchMedunderskriverFlyt(
                behandlingId,
                MEDUNDERSKRIVER_IDENT
            )

            assertThat(result.currentDelbehandling().medunderskriverFlyt).isEqualTo(MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER)
        }

        @Test
        fun `flere kall til switchMedunderskriverFlyt fra saksbehandler er idempotent`() {
            val behandling = simpleInsert()
            val behandlingId = behandling.id

            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilYtelse(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling) } returns Unit

            behandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                behandlingId,
                MEDUNDERSKRIVER_IDENT,
                SAKSBEHANDLER_IDENT
            )

            behandlingService.switchMedunderskriverFlyt(
                behandlingId,
                SAKSBEHANDLER_IDENT
            )

            val result = behandlingService.switchMedunderskriverFlyt(
                behandlingId,
                SAKSBEHANDLER_IDENT
            )

            assertThat(result.currentDelbehandling().medunderskriverFlyt).isEqualTo(MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER)
        }

        @Test
        fun `flere kall til switchMedunderskriverFlyt fra medunderskriver er idempotent`() {
            val behandling = simpleInsert()
            val behandlingId = behandling.id

            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns MEDUNDERSKRIVER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilYtelse(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling) } returns Unit

            behandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                behandlingId,
                MEDUNDERSKRIVER_IDENT,
                SAKSBEHANDLER_IDENT,
                MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER
            )

            behandlingService.switchMedunderskriverFlyt(
                behandlingId,
                MEDUNDERSKRIVER_IDENT
            )

            val result = behandlingService.switchMedunderskriverFlyt(
                behandlingId,
                MEDUNDERSKRIVER_IDENT
            )

            assertThat(result.currentDelbehandling().medunderskriverFlyt).isEqualTo(MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER)
        }
    }

    private fun simpleInsert(
        dokumentEnhetId: Boolean = false,
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
            kildesystem = Fagsystem.K9,
            forrigeBehandlendeEnhet = "0101",
            brukersHenvendelseMottattNavDato = LocalDate.now()
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
            kildesystem = Fagsystem.K9,
            kildeReferanse = "abc",
            mottakId = mottak.id,
            delbehandlinger = setOf(Delbehandling(
                utfall = when {
                    trukket -> Utfall.TRUKKET
                    utfall -> Utfall.AVVIST
                    else -> null
                },
                hjemler = if (hjemler) mutableSetOf(
                    Registreringshjemmel.ANDRE_TRYGDEAVTALER
                ) else mutableSetOf(),
                dokumentEnhetId = if (dokumentEnhetId) DOKUMENTENHET_ID else null,
                avsluttetAvSaksbehandler = if (fullfoert) LocalDateTime.now() else null,
            )),
            mottattFoersteinstans = LocalDate.now(),
            avsenderEnhetFoersteinstans = "enhet"
        )

        behandlingRepository.save(behandling)

        testEntityManager.flush()
        testEntityManager.clear()

        return behandling
    }
}
