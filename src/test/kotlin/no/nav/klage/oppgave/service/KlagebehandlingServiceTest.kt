package no.nav.klage.oppgave.service

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import io.mockk.mockk
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.kodeverk.*
import no.nav.klage.oppgave.exceptions.*
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import no.nav.klage.oppgave.repositories.MottakRepository
import no.nav.klage.oppgave.repositories.SaksbehandlerRepository
import no.nav.klage.oppgave.util.TokenUtil
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

@ActiveProfiles("local")
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class KlagebehandlingServiceTest {

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
    lateinit var klagebehandlingRepository: KlagebehandlingRepository

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

    private val dokumentService: DokumentService = mockk()

    private val tokenUtil: TokenUtil = mockk()

    lateinit var klagebehandlingService: KlagebehandlingService

    private val SAKSBEHANDLER_IDENT = "SAKSBEHANDLER_IDENT"
    private val MEDUNDERSKRIVER_IDENT = "MEDUNDERSKRIVER_IDENT"
    private val MELLOMLAGER_ID = "MELLOMLAGER_ID"

    @BeforeEach
    fun setup() {
        klagebehandlingService = KlagebehandlingService(
            klagebehandlingRepository,
            tilgangService,
            applicationEventPublisher,
            dokumentService,
            tokenUtil
        )
    }

    @Nested
    inner class GetKlagebehandlingForUpdate {
        @Test
        fun `getKlagebehandlingForUpdate ok`() {
            val klage = simpleInsert()

            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTil(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit

            assertThat(
                klagebehandlingService.getKlagebehandlingForUpdate(
                    klagebehandlingId = klage.id,
                    ignoreCheckSkrivetilgang = true
                )
            ).isEqualTo(klage)
        }

    @Test
    fun `getKlagebehandlingForUpdate sjekker skrivetilgang, fanger riktig exception`() {
        val klage = simpleInsert()

            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTil(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klage) }.throws(
                KlagebehandlingAvsluttetException("")
            )

            assertThrows<KlagebehandlingAvsluttetException> { klagebehandlingService.getKlagebehandlingForUpdate(klage.id) }
        }
    }

    @Nested
    inner class SetMedunderskriverIdent {
        @Test
        fun `setMedunderskriverIdent kan sette medunderskriver til null`() {
            val klagebehandling = simpleInsert()
            val klagebehandlingId = klagebehandling.id

            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTil(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit


            klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                klagebehandlingId,
                MEDUNDERSKRIVER_IDENT,
                SAKSBEHANDLER_IDENT
            )

            val result = klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                klagebehandlingId,
                null,
                SAKSBEHANDLER_IDENT
            )

            assert(result.medunderskriver?.saksbehandlerident == null)
            assert(result.medunderskriverHistorikk.size == 1)
        }
    }

    @Nested
    inner class SwitchMedunderskriverFlyt {
        @Test
        fun `switchMedunderskriverFlyt gir forventet feil når bruker er saksbehandler og medunderskriver ikke er satt`() {
            val klagebehandling = simpleInsert()
            val klagebehandlingId = klagebehandling.id

            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit

            assertThrows<KlagebehandlingManglerMedunderskriverException> {
                klagebehandlingService.switchMedunderskriverFlyt(
                    klagebehandlingId,
                    SAKSBEHANDLER_IDENT
                )
            }
        }

        @Test
        fun `switchMedunderskriverFlyt gir forventet status når bruker er saksbehandler og medunderskriver er satt`() {
            val klagebehandling = simpleInsert()
            val klagebehandlingId = klagebehandling.id

            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

            klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                klagebehandlingId,
                MEDUNDERSKRIVER_IDENT,
                SAKSBEHANDLER_IDENT
            )

            val result = klagebehandlingService.switchMedunderskriverFlyt(
                klagebehandlingId,
                SAKSBEHANDLER_IDENT
            )

            assert(result.medunderskriverFlyt == MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER)
        }

        @Test
        fun `switchMedunderskriverFlyt gir forventet status når bruker er medunderskriver`() {
            val klagebehandling = simpleInsert()
            val klagebehandlingId = klagebehandling.id

            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns MEDUNDERSKRIVER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

            klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                klagebehandlingId,
                MEDUNDERSKRIVER_IDENT,
                SAKSBEHANDLER_IDENT,
                MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER
            )

            val result = klagebehandlingService.switchMedunderskriverFlyt(
                klagebehandlingId,
                MEDUNDERSKRIVER_IDENT
            )

            assert(result.medunderskriverFlyt == MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER)
        }

        @Test
        fun `flere kall til switchMedunderskriverFlyt fra saksbehandler er idempotent`() {
            val klagebehandling = simpleInsert()
            val klagebehandlingId = klagebehandling.id

            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

            klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                klagebehandlingId,
                MEDUNDERSKRIVER_IDENT,
                SAKSBEHANDLER_IDENT
            )

            klagebehandlingService.switchMedunderskriverFlyt(
                klagebehandlingId,
                SAKSBEHANDLER_IDENT
            )

            val result = klagebehandlingService.switchMedunderskriverFlyt(
                klagebehandlingId,
                SAKSBEHANDLER_IDENT
            )

            assert(result.medunderskriverFlyt == MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER)
        }

        @Test
        fun `flere kall til switchMedunderskriverFlyt fra medunderskriver er idempotent`() {
            val klagebehandling = simpleInsert()
            val klagebehandlingId = klagebehandling.id

            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns MEDUNDERSKRIVER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

            klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
                klagebehandlingId,
                MEDUNDERSKRIVER_IDENT,
                SAKSBEHANDLER_IDENT,
                MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER
            )

            klagebehandlingService.switchMedunderskriverFlyt(
                klagebehandlingId,
                MEDUNDERSKRIVER_IDENT
            )

            val result = klagebehandlingService.switchMedunderskriverFlyt(
                klagebehandlingId,
                MEDUNDERSKRIVER_IDENT
            )

            assert(result.medunderskriverFlyt == MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER)
        }
    }

    @Nested
    inner class FerdigstillKlagebehandling {

        @Test
        fun `Forsøk på avslutting av klagebehandling som allerede er ferdigstilt i Joark skal ikke lykkes`() {
            val klagebehandling = simpleInsert(mellomlagerId = true, fullfoert = true)
            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

            assertThrows<KlagebehandlingFinalizedException> {
                klagebehandlingService.ferdigstillKlagebehandling(
                    klagebehandling.id,
                    SAKSBEHANDLER_IDENT
                )
            }
        }

        @Test
        fun `Forsøk på avslutting av klagebehandling som ikke har mellomlagret dokument skal ikke lykkes`() {
            val klagebehandling = simpleInsert(false)
            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

            assertThrows<ResultatDokumentNotFoundException> {
                klagebehandlingService.ferdigstillKlagebehandling(
                    klagebehandling.id,
                    SAKSBEHANDLER_IDENT
                )
            }
        }

        @Test
        fun `Forsøk på avslutting av klagebehandling som ikke har utfall skal ikke lykkes`() {
            val klagebehandling = simpleInsert(mellomlagerId = true, fullfoert = false, utfall = false)
            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

            assertThrows<ValidationException> {
                klagebehandlingService.ferdigstillKlagebehandling(
                    klagebehandling.id,
                    SAKSBEHANDLER_IDENT
                )
            }
        }

        @Test
        fun `Forsøk på avslutting av klagebehandling som ikke har hjemler skal ikke lykkes`() {
            val klagebehandling = simpleInsert(mellomlagerId = true, fullfoert = false, utfall = true, hjemler = false)
            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

            assertThrows<ValidationException> {
                klagebehandlingService.ferdigstillKlagebehandling(
                    klagebehandling.id,
                    SAKSBEHANDLER_IDENT
                )
            }
        }

        @Test
        fun `Forsøk på avslutting av klagebehandling som er trukket og som ikke har hjemler skal lykkes`() {
            val klagebehandling = simpleInsert(
                mellomlagerId = true,
                fullfoert = false,
                utfall = true,
                hjemler = false,
                trukket = true
            )
            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

            val result = klagebehandlingService.ferdigstillKlagebehandling(
                klagebehandling.id,
                MEDUNDERSKRIVER_IDENT
            )
            assert(result.avsluttetAvSaksbehandler != null)
        }

        @Test
        fun `Forsøk på avslutting av klagebehandling som er riktig utfylt skal lykkes`() {
            val klagebehandling = simpleInsert()
            every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
            every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
            every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit


            val result = klagebehandlingService.ferdigstillKlagebehandling(
                klagebehandling.id,
                MEDUNDERSKRIVER_IDENT
            )
            assert(result.avsluttetAvSaksbehandler != null)
        }
    }


    private fun simpleInsert(
        mellomlagerId: Boolean = true,
        fullfoert: Boolean = false,
        utfall: Boolean = true,
        hjemler: Boolean = true,
        trukket: Boolean = false
    ): Klagebehandling {
        val mottak = Mottak(
            tema = Tema.OMS,
            type = Type.KLAGE,
            klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
            kildeReferanse = "1234234",
            oversendtKaDato = LocalDateTime.now(),
            kildesystem = Fagsystem.K9,
            ytelse = "ABC"
        )

        mottakRepository.save(mottak)

        val klage = Klagebehandling(
            klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
            sakenGjelder = SakenGjelder(
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                skalMottaKopi = false
            ),
            tema = Tema.OMS,
            type = Type.KLAGE,
            frist = LocalDate.now(),
            hjemler = if (hjemler) mutableSetOf(
                Hjemmel.FTL_8_7
            ) else mutableSetOf(),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            kildesystem = Fagsystem.K9,
            mottakId = mottak.id,
            vedtak = Vedtak(
                utfall = when {
                    trukket -> Utfall.TRUKKET
                    utfall -> Utfall.AVVIST
                    else -> null
                },
                hjemler = if (hjemler) mutableSetOf(
                    Hjemmel.FTL
                ) else mutableSetOf(),
                mellomlagerId = if (mellomlagerId) MELLOMLAGER_ID else null
            ),
            avsluttetAvSaksbehandler = if (fullfoert) LocalDateTime.now() else null
        )

        klagebehandlingRepository.save(klage)

        testEntityManager.flush()
        testEntityManager.clear()

        return klage
    }
}
