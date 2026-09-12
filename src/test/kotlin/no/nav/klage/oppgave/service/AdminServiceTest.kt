package no.nav.klage.oppgave.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.klage.kodeverk.Enhet
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerEnhet
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerEnheter
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerPersonligInfo
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerSluttdato
import no.nav.slackposter.Severity
import no.nav.slackposter.SlackClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class AdminServiceTest {
    private val klageLookupGateway = mockk<KlageLookupGateway>()
    private val slackClient = mockk<SlackClient>(relaxed = true)

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
            kabalInnstillingerService = mockk(relaxed = true),
            applicationEventPublisher = mockk(relaxed = true),
            entityManager = mockk(relaxed = true),
            schedulerHealthGate = mockk(relaxed = true),
            merkantilRepository = mockk(relaxed = true),
            sakPersongalleriRepository = mockk(relaxed = true),
            klageLookupGateway = klageLookupGateway,
            personProtectionRepository = mockk(relaxed = true),
            transactionTemplate = mockk(relaxed = true),
        ).apply {
            klageBackendGroupId = "klage-backend-group-id"
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
