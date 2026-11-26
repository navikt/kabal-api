package no.nav.klage.oppgave.eventlisteners

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifyOrder
import no.nav.klage.dokument.service.DokumentUnderArbeidService
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.clients.kant.KantApiClient
import no.nav.klage.oppgave.clients.klagenotificationsapi.KlageNotificationsApiClient
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Feilregistrering
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.repositories.MeldingRepository
import no.nav.klage.oppgave.repositories.MergedDocumentRepository
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.KantService
import no.nav.klage.oppgave.service.KlankeService
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class CleanupAfterBehandlingEventListenerTest {
    private val meldingRepository = mockk<MeldingRepository>(relaxed = true)
    private val kafkaEventRepository = mockk<KafkaEventRepository>(relaxed = true)
    private val kakaApiGateway = mockk<KakaApiGateway>(relaxed = true)
    private val dokumentUnderArbeidService = mockk<DokumentUnderArbeidService>(relaxed = true)
    private val behandlingRepository = mockk<BehandlingRepository>(relaxed = true)
    private val klankeService = mockk<KlankeService>(relaxed = true)
    private val behandlingService = mockk<BehandlingService>(relaxed = true)
    private val mergedDocumentRepository = mockk<MergedDocumentRepository>(relaxed = true)
    private val klageNotificationsApiClient = mockk<KlageNotificationsApiClient>(relaxed = true)
    private val kantApiClient = mockk<KantApiClient>()
    private val kantService =
        KantService(
            kantApiClient = kantApiClient,
            useKantForOldTypesDate = LocalDate.now(),
        )
    private val listener =
        CleanupAfterBehandlingEventListener(
            meldingRepository = meldingRepository,
            kafkaEventRepository = kafkaEventRepository,
            kakaApiGateway = kakaApiGateway,
            dokumentUnderArbeidService = dokumentUnderArbeidService,
            behandlingRepository = behandlingRepository,
            klankeService = klankeService,
            kantService = kantService,
            behandlingService = behandlingService,
            mergedDocumentRepository = mergedDocumentRepository,
            klageNotificationsApiClient = klageNotificationsApiClient,
            systembrukerIdent = "synthetic-system-user",
        )

    @Test
    fun `feilregistrering deletes Kant using its reference and continues cleanup`() {
        val kantReference = UUID.randomUUID()
        val behandling = kantBehandling(kantReference)
        val behandlingId = behandling.id
        every { kantApiClient.deleteSaksdata(kantReference) } just runs
        setupCleanupMocks(behandlingId, behandling)

        listener.cleanupAfterBehandling(
            event(
                behandling = behandling,
                felt = BehandlingChangedEvent.Felt.FEILREGISTRERING,
            ),
        )

        verify(exactly = 1) { kantApiClient.deleteSaksdata(kantReference) }
        verifyOrder {
            dokumentUnderArbeidService.findDokumenterNotFinished(behandlingId = behandlingId, checkReadAccess = false)
            kantApiClient.deleteSaksdata(kantReference)
        }
        verify(exactly = 1) { meldingRepository.deleteAllById(emptyList()) }
        verify(exactly = 1) { klageNotificationsApiClient.deleteNotificationsForBehandling(behandlingId) }
    }

    @Test
    fun `feilregistrering without Kant reference does not call Kant`() {
        val behandling = kantBehandling(kantReference = null)
        setupCleanupMocks(behandling.id, behandling)

        listener.cleanupAfterBehandling(
            event(
                behandling = behandling,
                felt = BehandlingChangedEvent.Felt.FEILREGISTRERING,
            ),
        )

        verify(exactly = 0) { kantApiClient.deleteSaksdata(any()) }
    }

    @Test
    fun `feilregistrering for behandling without Kant support does not call Kant`() {
        val behandling = mockk<Klagebehandling>(relaxed = true)
        val behandlingId = UUID.randomUUID()
        every { behandling.id } returns behandlingId
        every { behandling.feilregistrering } returns feilregistrering
        every { behandling.fagsystem } returns Fagsystem.FS36
        every { behandling.gosysOppgaveRequired } returns true
        setupCleanupMocks(behandlingId = behandlingId, behandling = behandling)

        listener.cleanupAfterBehandling(
            event(
                behandling = behandling,
                felt = BehandlingChangedEvent.Felt.FEILREGISTRERING,
            ),
        )

        verify(exactly = 0) { kantApiClient.deleteSaksdata(any()) }
    }

    private fun kantBehandling(kantReference: UUID?): AnkebehandlingEtter2027 {
        val behandling = mockk<AnkebehandlingEtter2027>(relaxed = true)
        every { behandling.id } returns UUID.fromString("7c920b33-d05b-4a9d-b314-3ca4251ba3d8")
        every { behandling.kantReference } returns kantReference
        every { behandling.feilregistrering } returns feilregistrering
        every { behandling.fagsystem } returns Fagsystem.FS36
        every { behandling.gosysOppgaveRequired } returns true
        return behandling
    }

    private fun setupCleanupMocks(
        behandlingId: UUID,
        behandling: Behandling,
    ) {
        every {
            dokumentUnderArbeidService.findDokumenterNotFinished(
                behandlingId = behandlingId,
                checkReadAccess = false,
            )
        } returns emptyList()
        every { meldingRepository.findByBehandlingIdOrderByCreatedDesc(behandlingId = behandlingId) } returns emptyList()
        every { behandlingRepository.save(behandling) } returns behandling
    }

    private fun event(
        behandling: Behandling,
        felt: BehandlingChangedEvent.Felt,
    ) = BehandlingChangedEvent(
        behandling = behandling,
        changeList =
            listOf(
                BehandlingChangedEvent.Change(
                    saksbehandlerident = "synthetic-caseworker",
                    felt = felt,
                    behandlingId = behandling.id,
                ),
            ),
    )

    companion object {
        private val feilregistrering =
            Feilregistrering(
                navIdent = "synthetic-caseworker",
                navn = null,
                registered = LocalDateTime.parse("2026-01-01T10:00:00"),
                reason = "synthetic reason",
                fagsystem = Fagsystem.FS36,
            )
    }
}
