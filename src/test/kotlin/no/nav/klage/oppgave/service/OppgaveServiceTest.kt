package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.view.MineFerdigstilteOppgaverQueryParams
import no.nav.klage.oppgave.api.view.Rekkefoelge
import no.nav.klage.oppgave.api.view.Sortering
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.MottakRepository
import org.assertj.core.api.Assertions.assertThat
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

    lateinit var oppgaveService: OppgaveService

    @BeforeEach
    fun setup() {
        oppgaveService = OppgaveService(
            behandlingRepository = behandlingRepository,
        )
    }

    @Test
    fun `get ferdigstilte without specific query works`() {
        val behandling = simpleInsert(
            type = Type.KLAGE,
            ytelse = Ytelse.OMS_OMP,
            registreringshjemmelList = emptyList(),
        )

        val results = oppgaveService.getFerdigstilteOppgaverForNavIdent(
            MineFerdigstilteOppgaverQueryParams(
                typer = emptyList(),
                ytelser = emptyList(),
                registreringshjemler = emptyList(),
                rekkefoelge = Rekkefoelge.STIGENDE,
                sortering = Sortering.AVSLUTTET_AV_SAKSBEHANDLER,
                ferdigstiltFrom = LocalDate.now().minusDays(1),
                ferdigstiltTo = LocalDate.now().plusDays(1),
            )
        )

        assertThat(results.behandlinger).containsExactly(behandling.id)
    }

    @Test
    fun `get ferdigstilte with specific types works`() {
        simpleInsert(
            type = Type.KLAGE,
            ytelse = Ytelse.OMS_OMP,
            registreringshjemmelList = emptyList(),
        )

        simpleInsert(
            type = Type.ANKE,
            ytelse = Ytelse.OMS_OMP,
            registreringshjemmelList = emptyList(),
        )

        simpleInsert(
            type = Type.ANKE,
            ytelse = Ytelse.OMS_PLS,
            registreringshjemmelList = emptyList(),
        )

        val results = oppgaveService.getFerdigstilteOppgaverForNavIdent(
            MineFerdigstilteOppgaverQueryParams(
                typer = listOf(Type.KLAGE.id, Type.ANKE.id),
                ytelser = listOf(Ytelse.OMS_OMP.id),
                registreringshjemler = emptyList(),
                rekkefoelge = Rekkefoelge.STIGENDE,
                sortering = Sortering.AVSLUTTET_AV_SAKSBEHANDLER,
                ferdigstiltFrom = LocalDate.now().minusDays(1),
                ferdigstiltTo = LocalDate.now().plusDays(1),
            )
        )

        assertThat(results.behandlinger).hasSize(2)
    }

    @Test
    fun `get ferdigstilte with specific ytelse works`() {
        simpleInsert(
            type = Type.KLAGE,
            ytelse = Ytelse.OMS_OMP,
            registreringshjemmelList = emptyList(),
        )

        simpleInsert(
            type = Type.ANKE,
            ytelse = Ytelse.OMS_PSB,
            registreringshjemmelList = emptyList(),
        )

        simpleInsert(
            type = Type.ANKE,
            ytelse = Ytelse.OMS_PLS,
            registreringshjemmelList = emptyList(),
        )

        val results = oppgaveService.getFerdigstilteOppgaverForNavIdent(
            MineFerdigstilteOppgaverQueryParams(
                typer = emptyList(),
                ytelser = listOf(Ytelse.OMS_OMP.id, Ytelse.OMS_PLS.id),
                registreringshjemler = emptyList(),
                rekkefoelge = Rekkefoelge.STIGENDE,
                sortering = Sortering.AVSLUTTET_AV_SAKSBEHANDLER,
                ferdigstiltFrom = LocalDate.now().minusDays(1),
                ferdigstiltTo = LocalDate.now().plusDays(1),
            )
        )

        assertThat(results.behandlinger).hasSize(2)
    }

    @Test
    fun `get ferdigstilte with specific ytelse and type works`() {
        val behandling = simpleInsert(
            type = Type.KLAGE,
            ytelse = Ytelse.OMS_OMP,
            registreringshjemmelList = emptyList(),
        )

        simpleInsert(
            type = Type.KLAGE,
            ytelse = Ytelse.OMS_OLP,
            registreringshjemmelList = emptyList(),
        )

        simpleInsert(
            type = Type.ANKE,
            ytelse = Ytelse.OMS_OMP,
            registreringshjemmelList = emptyList(),
        )

        val results = oppgaveService.getFerdigstilteOppgaverForNavIdent(
            MineFerdigstilteOppgaverQueryParams(
                typer = listOf(Type.KLAGE.id),
                ytelser = listOf(Ytelse.OMS_OMP.id),
                registreringshjemler = emptyList(),
                rekkefoelge = Rekkefoelge.STIGENDE,
                sortering = Sortering.AVSLUTTET_AV_SAKSBEHANDLER,
                ferdigstiltFrom = LocalDate.now().minusDays(1),
                ferdigstiltTo = LocalDate.now().plusDays(1),
            )
        )

        assertThat(results.behandlinger).containsExactly(behandling.id)
    }

    @Test
    fun `get ferdigstilte with specific registreringshjemmel works`() {
        val behandling = simpleInsert(
            type = Type.KLAGE,
            ytelse = Ytelse.OMS_OMP,
            registreringshjemmelList = listOf(Registreringshjemmel.ANDRE_TRYGDEAVTALER),
        )

        simpleInsert(
            type = Type.KLAGE,
            ytelse = Ytelse.OMS_OMP,
            registreringshjemmelList = emptyList(),
        )

        val results = oppgaveService.getFerdigstilteOppgaverForNavIdent(
            MineFerdigstilteOppgaverQueryParams(
                typer = emptyList(),
                ytelser = emptyList(),
                registreringshjemler = listOf(Registreringshjemmel.ANDRE_TRYGDEAVTALER.id),
                rekkefoelge = Rekkefoelge.STIGENDE,
                sortering = Sortering.AVSLUTTET_AV_SAKSBEHANDLER,
                ferdigstiltFrom = LocalDate.now().minusDays(1),
                ferdigstiltTo = LocalDate.now().plusDays(1),
            )
        )

        assertThat(results.behandlinger).containsExactly(behandling.id)
    }

    private fun simpleInsert(
        type: Type,
        ytelse: Ytelse,
        registreringshjemmelList: List<Registreringshjemmel>,
    ): Behandling {
        val mottak = Mottak(
            ytelse = ytelse,
            type = type,
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

        val behandling = when (type) {
            Type.KLAGE -> {
                Klagebehandling(
                    klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
                    sakenGjelder = SakenGjelder(
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                        skalMottaKopi = false
                    ),
                    ytelse = ytelse,
                    type = type,
                    frist = LocalDate.now(),
                    hjemler = mutableSetOf(),
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
                    utfall = Utfall.STADFESTELSE,
                    extraUtfallSet = emptySet(),
                    registreringshjemler = registreringshjemmelList.toMutableSet(),
                    avsluttetAvSaksbehandler = LocalDateTime.now(),
                    previousSaksbehandlerident = "C78901",
                )
            }

            Type.ANKE -> {
                Ankebehandling(
                    klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
                    sakenGjelder = SakenGjelder(
                        partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                        skalMottaKopi = false
                    ),
                    ytelse = ytelse,
                    type = type,
                    frist = LocalDate.now(),
                    hjemler = mutableSetOf(),
                    created = now,
                    modified = now,
                    mottattKlageinstans = now,
                    fagsystem = Fagsystem.K9,
                    fagsakId = "123",
                    kildeReferanse = "abc",
                    mottakId = mottak.id,
                    kakaKvalitetsvurderingId = UUID.randomUUID(),
                    kakaKvalitetsvurderingVersion = 2,
                    utfall = Utfall.STADFESTELSE,
                    extraUtfallSet = emptySet(),
                    registreringshjemler = registreringshjemmelList.toMutableSet(),
                    avsluttetAvSaksbehandler = LocalDateTime.now(),
                    previousSaksbehandlerident = "C78901",
                    klageBehandlendeEnhet = "1000",
                    sourceBehandlingId = UUID.randomUUID(),
                )
            }

            Type.ANKE_I_TRYGDERETTEN -> TODO()
        }

        behandlingRepository.save(behandling)

        testEntityManager.flush()
        testEntityManager.clear()

        return behandling
    }
}
