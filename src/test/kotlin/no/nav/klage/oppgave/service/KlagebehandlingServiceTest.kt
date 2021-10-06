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
import no.nav.klage.oppgave.exceptions.KlagebehandlingAvsluttetException
import no.nav.klage.oppgave.exceptions.KlagebehandlingManglerMedunderskriverException
import no.nav.klage.oppgave.exceptions.KlagebehandlingSamtidigEndretException
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import no.nav.klage.oppgave.repositories.MottakRepository
import no.nav.klage.oppgave.repositories.SaksbehandlerRepository
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
    fun `getKlagebehandlingForUpdate slår til på optimistic locking`() {
        val klage = simpleInsert()

        every { tilgangService.verifyInnloggetSaksbehandlersTilgangTil(any()) } returns Unit
        every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit

        assertThrows<KlagebehandlingSamtidigEndretException> {
            klagebehandlingService.getKlagebehandlingForUpdate(
                klage.id,
                1L,
                true
            )
        }
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

    @Test
    fun `setMedunderskriverIdent kan sette medunderskriver til null`() {
        val klagebehandling = simpleInsert()
        val klagebehandlingId = klagebehandling.id
        val medunderskriverIdent = "MEDUNDERSKRIVER"
        val utfoerendeSaksehandlerIdent = "SAKSBEHANDLER"

        every { tilgangService.verifyInnloggetSaksbehandlersTilgangTil(any()) } returns Unit
        every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
        every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit


        klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
            klagebehandlingId,
            medunderskriverIdent,
            utfoerendeSaksehandlerIdent
        )

        val result = klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
            klagebehandlingId,
            null,
            utfoerendeSaksehandlerIdent
        )

        assert(result.medunderskriver?.saksbehandlerident == null)
        assert(result.medunderskriverHistorikk.size == 1)
    }

    @Test
    fun `switchMedunderskriverFlyt gir forventet feil når bruker er saksbehandler og medunderskriver ikke er satt`() {
        val klagebehandling = simpleInsert()
        val klagebehandlingId = klagebehandling.id
        val utfoerendeSaksehandlerIdent = "SAKSBEHANDLER"

        every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns utfoerendeSaksehandlerIdent
        every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
        every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit

        assertThrows<KlagebehandlingManglerMedunderskriverException> {
            klagebehandlingService.switchMedunderskriverFlyt(
                klagebehandlingId,
                utfoerendeSaksehandlerIdent
            )
        }
    }

    @Test
    fun `switchMedunderskriverFlyt gir forventet status når bruker er saksbehandler og medunderskriver er satt`() {
        val klagebehandling = simpleInsert()
        val klagebehandlingId = klagebehandling.id
        val utfoerendeSaksehandlerIdent = "SAKSBEHANDLER"
        val medunderskriverIdent = "MEDUNDERSKRIVER"

        every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns utfoerendeSaksehandlerIdent
        every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
        every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
        every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

        klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
            klagebehandlingId,
            medunderskriverIdent,
            utfoerendeSaksehandlerIdent
        )

        val result = klagebehandlingService.switchMedunderskriverFlyt(
            klagebehandlingId,
            utfoerendeSaksehandlerIdent
        )

        assert(result.medunderskriverFlyt == MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER)
    }

    @Test
    fun `switchMedunderskriverFlyt gir forventet status når bruker er medunderskriver`() {
        val klagebehandling = simpleInsert()
        val klagebehandlingId = klagebehandling.id
        val utfoerendeSaksehandlerIdent = "SAKSBEHANDLER"
        val medunderskriverIdent = "MEDUNDERSKRIVER"

        every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns medunderskriverIdent
        every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
        every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
        every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

        klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
            klagebehandlingId,
            medunderskriverIdent,
            utfoerendeSaksehandlerIdent,
            MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER
        )

        val result = klagebehandlingService.switchMedunderskriverFlyt(
            klagebehandlingId,
            medunderskriverIdent
        )

        assert(result.medunderskriverFlyt == MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER)
    }

    @Test
    fun `flere kall til switchMedunderskriverFlyt fra saksbehandler er idempotent`() {
        val klagebehandling = simpleInsert()
        val klagebehandlingId = klagebehandling.id
        val utfoerendeSaksehandlerIdent = "SAKSBEHANDLER"
        val medunderskriverIdent = "MEDUNDERSKRIVER"

        every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns utfoerendeSaksehandlerIdent
        every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
        every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
        every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

        klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
            klagebehandlingId,
            medunderskriverIdent,
            utfoerendeSaksehandlerIdent
        )

        klagebehandlingService.switchMedunderskriverFlyt(
            klagebehandlingId,
            utfoerendeSaksehandlerIdent
        )

        val result = klagebehandlingService.switchMedunderskriverFlyt(
            klagebehandlingId,
            utfoerendeSaksehandlerIdent
        )

        assert(result.medunderskriverFlyt == MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER)
    }

    @Test
    fun `flere kall til switchMedunderskriverFlyt fra medunderskriver er idempotent`() {
        val klagebehandling = simpleInsert()
        val klagebehandlingId = klagebehandling.id
        val utfoerendeSaksehandlerIdent = "SAKSBEHANDLER"
        val medunderskriverIdent = "MEDUNDERSKRIVER"

        every { innloggetSaksbehandlerRepository.getInnloggetIdent() } returns medunderskriverIdent
        every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
        every { tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(any()) } returns Unit
        every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling) } returns Unit

        klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
            klagebehandlingId,
            medunderskriverIdent,
            utfoerendeSaksehandlerIdent,
            MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER
        )

        klagebehandlingService.switchMedunderskriverFlyt(
            klagebehandlingId,
            medunderskriverIdent
        )

        val result = klagebehandlingService.switchMedunderskriverFlyt(
            klagebehandlingId,
            medunderskriverIdent
        )

        assert(result.medunderskriverFlyt == MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER)
    }

    private fun simpleInsert(): Klagebehandling {
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
            versjon = 2L,
            klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
            sakenGjelder = SakenGjelder(
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                skalMottaKopi = false
            ),
            tema = Tema.OMS,
            type = Type.KLAGE,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(
                Hjemmel.FTL_8_7
            ),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            kildesystem = Fagsystem.K9,
            mottakId = mottak.id,
            vedtak = Vedtak(
                utfall = Utfall.AVVIST,
                hjemler = mutableSetOf(
                    Hjemmel.FTL
                )
            )
        )

        klagebehandlingRepository.save(klage)

        testEntityManager.flush()
        testEntityManager.clear()

        return klage
    }

}
