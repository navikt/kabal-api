package no.nav.klage.oppgave.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Tildeling
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerEnhet
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerEnheter
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerPersonligInfo
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerSluttdato
import no.nav.slackposter.Severity
import no.nav.slackposter.SlackClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class AdminServiceTest {
    private val klageLookupGateway = mockk<KlageLookupGateway>()
    private val slackClient = mockk<SlackClient>(relaxed = true)
    private val kabalInnstillingerService = mockk<KabalInnstillingerService>()

    private val adminService =
        AdminService(
            kafkaDispatcher = mockk(relaxed = true),
            behandlingRepository = mockk(relaxed = true),
            klagebehandlingRepository = mockk(relaxed = true),
            ankebehandlingFoer2027Repository = mockk(relaxed = true),
            omgjoeringskravbehandlingRepository = mockk(relaxed = true),
            dokumentUnderArbeidRepository = mockk(relaxed = true),
            behandlingEndretKafkaProducer = mockk(relaxed = true),
            fileApiClient = mockk(relaxed = true),
            innholdsfortegnelseService = mockk(relaxed = true),
            saksbehandlerService = mockk(relaxed = true),
            behandlingService = mockk(relaxed = true),
            klankeService = mockk(relaxed = true),
            tokenUtil = mockk(relaxed = true),
            systembrukerIdent = "SYSTEMBRUKER",
            personService = mockk(relaxed = true),
            minsideMicrofrontendService = mockk(relaxed = true),
            slackClient = slackClient,
            kabalInnstillingerService = kabalInnstillingerService,
            applicationEventPublisher = mockk(relaxed = true),
            entityManager = mockk(relaxed = true),
            schedulerHealthGate = mockk(relaxed = true),
            merkantilRepository = mockk(relaxed = true),
            sakPersongalleriRepository = mockk(relaxed = true),
            klageLookupGateway = klageLookupGateway,
            personProtectionRepository = mockk(relaxed = true),
            transactionTemplate = mockk(relaxed = true),
            safFacade = mockk(relaxed = true),
            kabalDocumentGateway = mockk(relaxed = true),
        ).apply {
            klageBackendGroupId = "klage-backend-group-id"
        }

    @Test
    fun `no single saksbehandler has all behandling hjemler`() {
        val unavailable = behandling(setOf(Hjemmel.FVL_11, Hjemmel.FVL_12))
        val available = behandling(setOf(Hjemmel.FVL_11))
        every {
            kabalInnstillingerService.getRegisteredHjemmelSetsForYtelse(ytelse = Ytelse.OMS_OMP, includeSE = false, onlyAnketeam = false)
        } returns setOf(setOf(Hjemmel.FVL_11), setOf(Hjemmel.FVL_12))

        val report = adminService.checkForUnavailableDueToHjemler(listOf(unavailable, available))

        assertThat(report).contains("Behandling-id: ${unavailable.id}, Hjemler:")
        assertThat(report).doesNotContain("Behandling-id: ${available.id}, Hjemler:")
        verify(exactly = 1) {
            kabalInnstillingerService.getRegisteredHjemmelSetsForYtelse(ytelse = Ytelse.OMS_OMP, includeSE = false, onlyAnketeam = false)
        }
    }

    @Test
    fun `behandling is available when one saksbehandler has all hjemler`() {
        val available = behandling(setOf(Hjemmel.FVL_11, Hjemmel.FVL_12))
        every {
            kabalInnstillingerService.getRegisteredHjemmelSetsForYtelse(ytelse = Ytelse.OMS_OMP, includeSE = false, onlyAnketeam = false)
        } returns setOf(setOf(Hjemmel.FVL_11, Hjemmel.FVL_12, Hjemmel.FVL_14))

        val report = adminService.checkForUnavailableDueToHjemler(listOf(available))

        assertThat(report).doesNotContain("Behandling-id: ${available.id}, Hjemler:")
    }

    @Test
    fun `unregistered hjemmel is reported even when another is registered`() {
        val behandling = behandling(setOf(Hjemmel.FVL_11, Hjemmel.FVL_12))
        every {
            kabalInnstillingerService.getRegisteredHjemmelSetsForYtelse(ytelse = Ytelse.OMS_OMP, includeSE = false, onlyAnketeam = false)
        } returns setOf(setOf(Hjemmel.FVL_11))

        val report = adminService.checkForUnavailableDueToHjemler(listOf(behandling))

        assertThat(report).contains("Utilgjengelige behandlinger på grunn av hjemler: \nBehandling-id: ${behandling.id}")
        assertThat(
            report,
        ).contains("Behandlinger med hjemler som ikke fins i innstillinger: \nBehandling-id: ${behandling.id}, Hjemler: [FVL_12]")
    }

    @Test
    fun `anketeam lookup is independent of ordinary lookup for the same ytelse`() {
        val anke = behandling(hjemler = setOf(element = Hjemmel.FVL_11), anke = true)
        val klage = behandling(setOf(Hjemmel.FVL_11))
        every {
            kabalInnstillingerService.getRegisteredHjemmelSetsForYtelse(ytelse = Ytelse.OMS_OMP, includeSE = false, onlyAnketeam = true)
        } returns emptySet()
        every {
            kabalInnstillingerService.getRegisteredHjemmelSetsForYtelse(ytelse = Ytelse.OMS_OMP, includeSE = false, onlyAnketeam = false)
        } returns setOf(setOf(Hjemmel.FVL_11))

        val report = adminService.checkForUnavailableDueToHjemler(listOf(anke, klage))

        assertThat(report).contains("Behandling-id: ${anke.id}, Hjemler:")
        assertThat(report).doesNotContain("Behandling-id: ${klage.id}, Hjemler:")
        verify(exactly = 1) {
            kabalInnstillingerService.getRegisteredHjemmelSetsForYtelse(ytelse = Ytelse.OMS_OMP, includeSE = false, onlyAnketeam = true)
        }
        verify(exactly = 1) {
            kabalInnstillingerService.getRegisteredHjemmelSetsForYtelse(ytelse = Ytelse.OMS_OMP, includeSE = false, onlyAnketeam = false)
        }
    }

    @Test
    fun `assigned to enhet remains a candidate while assigned to saksbehandler does not`() {
        val enhetOnly = behandling(hjemler = setOf(Hjemmel.FVL_11), saksbehandlerident = null)
        val assigned = behandling(hjemler = setOf(Hjemmel.FVL_11), saksbehandlerident = "Z123456")
        every {
            kabalInnstillingerService.getRegisteredHjemmelSetsForYtelse(ytelse = Ytelse.OMS_OMP, includeSE = false, onlyAnketeam = false)
        } returns emptySet()

        val report = adminService.checkForUnavailableDueToHjemler(listOf(enhetOnly, assigned))

        assertThat(report).contains("Behandling-id: ${enhetOnly.id}, Hjemler:")
        assertThat(report).doesNotContain("Behandling-id: ${assigned.id}, Hjemler:")
    }

    @Test
    fun `empty hjemler are unavailable and lookup failure is reported without declaring availability`() {
        val empty = behandling(emptySet())
        val failed = behandling(hjemler = setOf(element = Hjemmel.FVL_11), ytelse = Ytelse.OMS_PLS)
        every {
            kabalInnstillingerService.getRegisteredHjemmelSetsForYtelse(ytelse = Ytelse.OMS_OMP, includeSE = false, onlyAnketeam = false)
        } returns setOf(setOf(Hjemmel.FVL_11))
        every {
            kabalInnstillingerService.getRegisteredHjemmelSetsForYtelse(ytelse = Ytelse.OMS_PLS, includeSE = false, onlyAnketeam = false)
        } throws IllegalStateException("Settings unavailable")

        val report = adminService.checkForUnavailableDueToHjemler(listOf(empty, failed))

        assertThat(report).contains("Behandling-id: ${empty.id}, Hjemler:")
        assertThat(report).doesNotContain("Behandling-id: ${failed.id}, Hjemler:")
        assertThat(report).contains("Kunne ikke sjekke hjemler for ytelser:")
        assertThat(report).contains("Ytelse: ${Ytelse.OMS_PLS}, kun anketeam: false")
    }

    private fun behandling(
        hjemler: Set<Hjemmel>,
        ytelse: Ytelse = Ytelse.OMS_OMP,
        anke: Boolean = false,
        saksbehandlerident: String? = null,
    ): Behandling {
        val behandling: Behandling = if (anke) mockk<AnkebehandlingEtter2027>() else mockk<Klagebehandling>()
        every { behandling.id } returns UUID.randomUUID()
        every { behandling.ytelse } returns ytelse
        every { behandling.hjemler } returns hjemler
        every { behandling.tildeling } returns
            Tildeling(
                saksbehandlerident = saksbehandlerident,
                enhet = "4291",
                tidspunkt = LocalDateTime.now(),
            )
        return behandling
    }

    @Test
    fun `user with a single allowed enhet is kept`() {
        givenNoSluttdato("A123")
        givenEnheter("A123" to listOf(Enhet.E4291))

        assertThat(adminService.getUsersToRemove(setOf("A123"))).isEmpty()
    }

    @Test
    fun `user is kept when only one of several enheter is allowed`() {
        givenNoSluttdato("A123")
        givenEnheter("A123" to listOf(Enhet.E4703, Enhet.E4408, Enhet.E4291))

        assertThat(adminService.getUsersToRemove(setOf("A123"))).isEmpty()
    }

    @Test
    fun `user is kept when the allowed enhet is a styringsenhet among several enheter`() {
        givenNoSluttdato("A123")
        givenEnheter("A123" to listOf(Enhet.E4703, Enhet.E4200))

        assertThat(adminService.getUsersToRemove(setOf("A123"))).isEmpty()
    }

    @Test
    fun `user is removed when none of several enheter is allowed`() {
        givenNoSluttdato("A123")
        givenEnheter("A123" to listOf(Enhet.E4703, Enhet.E4408))

        assertThat(adminService.getUsersToRemove(setOf("A123"))).containsExactly("A123")
    }

    @Test
    fun `only users without any allowed enhet are removed`() {
        givenNoSluttdato("A123", "B456", "C789")
        givenEnheter(
            "A123" to listOf(Enhet.E4703, Enhet.E4291),
            "B456" to listOf(Enhet.E4703, Enhet.E4408),
            "C789" to listOf(Enhet.E4200),
        )

        assertThat(adminService.getUsersToRemove(setOf("A123", "B456", "C789"))).containsExactly("B456")
    }

    @Test
    fun `user with an empty enhet list is kept, since the lookup gave no answer`() {
        givenNoSluttdato("A123")
        givenEnheter("A123" to emptyList())

        assertThat(adminService.getUsersToRemove(setOf("A123"))).isEmpty()
    }

    @Test
    fun `user missing from the enhet lookup is kept`() {
        givenNoSluttdato("A123")
        every { klageLookupGateway.getEnheterForNavIdentList(any()) } returns emptyList()

        assertThat(adminService.getUsersToRemove(setOf("A123"))).isEmpty()
    }

    @Test
    fun `user who left Nav is removed without an enhet lookup`() {
        every { klageLookupGateway.getSluttdatoForNavIdentList(any()) } returns
            listOf(
                SaksbehandlerSluttdato(
                    navIdent = "A123",
                    sluttdato = LocalDate.now().minusMonths(1),
                ),
            )

        assertThat(adminService.getUsersToRemove(setOf("A123"))).containsExactly("A123")
    }

    @Test
    fun `no candidates gives no users to remove`() {
        assertThat(adminService.getUsersToRemove(emptySet())).isEmpty()
    }

    @Test
    fun `user is skipped when the primary enhet is missing from the enhet list`() {
        givenNoSluttdato("A123")
        givenEnheter("A123" to listOf(Enhet.E4408))
        givenPrimaryEnhet("A123" to Enhet.E4703)

        assertThat(adminService.getUsersToRemove(setOf("A123"))).isEmpty()
    }

    @Test
    fun `skipping one user does not affect the others`() {
        givenNoSluttdato("A123", "B456")
        givenEnheter(
            "A123" to listOf(Enhet.E4408),
            "B456" to listOf(Enhet.E4408),
        )
        givenPrimaryEnhet(
            "A123" to Enhet.E4703,
            "B456" to Enhet.E4408,
        )

        assertThat(adminService.getUsersToRemove(setOf("A123", "B456"))).containsExactly("B456")
    }

    @Test
    fun `user is skipped when the primary enhet is unknown`() {
        givenNoSluttdato("A123")
        givenEnheter("A123" to listOf(Enhet.E4408))
        givenPrimaryEnhet()

        assertThat(adminService.getUsersToRemove(setOf("A123"))).isEmpty()
    }

    @Test
    fun `a failing primary enhet lookup removes nobody`() {
        givenNoSluttdato("A123")
        givenEnheter("A123" to listOf(Enhet.E4408))
        every { klageLookupGateway.getUserInfoForNavIdentList(any()) } throws RuntimeException("Lookup is down")

        assertThat(adminService.getUsersToRemove(setOf("A123"))).isEmpty()

        verify { slackClient.postMessage(text = any(), severity = Severity.ERROR) }
    }

    @Test
    fun `a failing Slack notification does not break the run`() {
        givenNoSluttdato("A123")
        givenEnheter("A123" to listOf(Enhet.E4408))
        every { klageLookupGateway.getUserInfoForNavIdentList(any()) } throws RuntimeException("Lookup is down")
        every { slackClient.postMessage(text = any(), severity = any()) } throws RuntimeException("Slack is down")

        assertThat(adminService.getUsersToRemove(setOf("A123"))).isEmpty()
    }

    @Test
    fun `no Slack notification when the primary enhet lookup works`() {
        givenNoSluttdato("A123")
        givenEnheter("A123" to listOf(Enhet.E4408))

        assertThat(adminService.getUsersToRemove(setOf("A123"))).containsExactly("A123")

        verify(exactly = 0) { slackClient.postMessage(text = any(), severity = any()) }
    }

    private fun givenNoSluttdato(vararg navIdentList: String) {
        every { klageLookupGateway.getSluttdatoForNavIdentList(any()) } returns
            navIdentList.map { SaksbehandlerSluttdato(navIdent = it, sluttdato = null) }
    }

    private fun givenEnheter(vararg enheterPerNavIdent: Pair<String, List<Enhet>>) {
        every { klageLookupGateway.getEnheterForNavIdentList(any()) } returns
            enheterPerNavIdent.map { (navIdent, enheter) ->
                SaksbehandlerEnheter(
                    navIdent = navIdent,
                    enheter = enheter.map { it.toSaksbehandlerEnhet() },
                )
            }

        // Default to a primary enhet that agrees with the enhet list, so the consistency
        // check stays out of the way unless a test overrides it.
        givenPrimaryEnhet(
            *enheterPerNavIdent
                .mapNotNull { (navIdent, enheter) -> enheter.firstOrNull()?.let { navIdent to it } }
                .toTypedArray(),
        )
    }

    private fun givenPrimaryEnhet(vararg primaryEnhetPerNavIdent: Pair<String, Enhet>) {
        every { klageLookupGateway.getUserInfoForNavIdentList(any()) } returns
            primaryEnhetPerNavIdent.map { (navIdent, enhet) ->
                SaksbehandlerPersonligInfo(
                    navIdent = navIdent,
                    fornavn = "Fornavn",
                    etternavn = "Etternavn",
                    sammensattNavn = "Fornavn Etternavn",
                    enhet = enhet.toSaksbehandlerEnhet(),
                )
            }
    }

    private fun Enhet.toSaksbehandlerEnhet(): SaksbehandlerEnhet =
        SaksbehandlerEnhet(
            enhetId = navn,
            navn = beskrivelse,
        )
}
