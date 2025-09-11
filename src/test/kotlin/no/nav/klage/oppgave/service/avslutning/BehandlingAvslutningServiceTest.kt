package no.nav.klage.oppgave.service.avslutning

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import no.nav.klage.dokument.service.DokumentUnderArbeidCommonService
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFinishedInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFromKlanke
import no.nav.klage.oppgave.domain.behandling.*
import no.nav.klage.oppgave.domain.behandling.embedded.Ferdigstilling
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.service.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.util.*

class BehandlingAvslutningServiceTest {
    private val behandlingService = mockk<BehandlingService>()
    private val ankeITrygderettenbehandlingService = mockk<AnkeITrygderettenbehandlingService>(relaxed = true)
    private val behandlingEtterTrygderettenOpphevetService =
        mockk<BehandlingEtterTrygderettenOpphevetService>(relaxed = true)
    private val ankebehandlingService = mockk<AnkebehandlingService>(relaxed = true)
    private val fssProxyClient = mockk<KlageFssProxyClient>(relaxed = true)
    private val gosysOppgaveService = mockk<GosysOppgaveService>(relaxed = true)
    private val kafkaEventRepository = mockk<KafkaEventRepository>(relaxed = true)
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val systembrukerIdent = "system"
    private val dokumentUnderArbeidCommonService = mockk<DokumentUnderArbeidCommonService>(relaxed = true)

    private val behandlingAvslutningService = BehandlingAvslutningService(
        kafkaEventRepository = kafkaEventRepository,
        behandlingService = behandlingService,
        applicationEventPublisher = applicationEventPublisher,
        dokumentUnderArbeidCommonService = dokumentUnderArbeidCommonService,
        ankeITrygderettenbehandlingService = ankeITrygderettenbehandlingService,
        behandlingEtterTrygderettenOpphevetService = behandlingEtterTrygderettenOpphevetService,
        ankebehandlingService = ankebehandlingService,
        fssProxyClient = fssProxyClient,
        gosysOppgaveService = gosysOppgaveService,
        systembrukerIdent = systembrukerIdent
    )

    private val behandlingId = UUID.randomUUID()

    private val now = LocalDateTime.now()

    @BeforeEach
    fun before() {
        every {
            dokumentUnderArbeidCommonService.findHoveddokumenterOnBehandlingByMarkertFerdigNotNullAndFerdigstiltNull(
                any()
            )
        } returns emptySet()
    }

    @Test
    fun `throws exception if already closed`() {
        val behandling = mockk<Klagebehandling> {
            every { ferdigstilling } returns mockk { every { avsluttet } returns now }
        }

        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        assertThrows<BehandlingAvsluttetException> {
            behandlingAvslutningService.avsluttBehandling(behandlingId)
        }
    }

    @Test
    fun `Ankebehandling with innstilling avvist and fagsystem Infotrygd creates AnkeITrygderettenbehandling and updates Infotrygd`() {
        val behandling = spyk<Ankebehandling> {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { fagsystem } returns Fagsystem.IT01
            every { utfall } returns Utfall.INNSTILLING_STADFESTELSE
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
            every { createAnkeITrygderettenbehandlingInput() } returns mockk()
        }
        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        behandlingAvslutningService.avsluttBehandling(behandlingId)

        verify(exactly = 1) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
        verify(exactly = 1) {
            fssProxyClient.setToFinishedWithAppAccess(
                any(), SakFinishedInput(
                    status = SakFinishedInput.Status.VIDERESENDT_TR,
                    nivaa = SakFinishedInput.Nivaa.KA,
                    typeResultat = SakFinishedInput.TypeResultat.INNSTILLING_2,
                    utfall = SakFinishedInput.Utfall.AVSLAG,
                    mottaker = SakFinishedInput.Mottaker.TRYGDERETTEN,
                    saksbehandlerIdent = "ident"
                )
            )
        }
        verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
        verify(exactly = 0) { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) }
        verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
        verify(exactly = 0) { kafkaEventRepository.save(any()) }
    }

    @Test
    fun `AnkeITrygderettenbehandling with utfall HENVIST creates new Ankebehandling`() {
        val behandling = spyk<AnkeITrygderettenbehandling> {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { utfall } returns Utfall.HENVIST
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
        }
        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        behandlingAvslutningService.avsluttBehandling(behandlingId)

        verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
        verify(exactly = 1) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
        verify(exactly = 0) { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) }
        verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
        verify(exactly = 0) { kafkaEventRepository.save(any()) }
    }

    @Test
    fun `AnkeITrygderettenbehandling with utfall HENVIST and gosysOppgaveId creates new Ankebehandling and notifies GosysOppgave`() {
        val behandling = spyk<AnkeITrygderettenbehandling> {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { utfall } returns Utfall.HENVIST
            every { gosysOppgaveId } returns 123L
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
        }
        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        behandlingAvslutningService.avsluttBehandling(behandlingId)

        verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
        verify(exactly = 1) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        verify(exactly = 1) {
            gosysOppgaveService.addKommentar(
                any(),
                "Klageinstansen har opprettet ny behandling i Kabal etter at Trygderetten har henvist saken.",
                any(),
                any()
            )
        }
        verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
        verify(exactly = 0) { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) }
        verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
        verify(exactly = 0) { kafkaEventRepository.save(any()) }
    }

    @Test
    fun `AnkeITrygderettenbehandling tagged with nyAnkebehandlingKA creates new Ankebehandling`() {
        val behandling = spyk<AnkeITrygderettenbehandling> {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { nyAnkebehandlingKA } returns now
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
        }
        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        behandlingAvslutningService.avsluttBehandling(behandlingId)

        verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
        verify(exactly = 1) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
        verify(exactly = 0) { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) }
        verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
        verify(exactly = 0) { kafkaEventRepository.save(any()) }
    }

    @Test
    fun `AnkeITrygderettenbehandling tagged with nyAnkebehanldingKA and gosysOppgaveId creates new Ankebehandling and notifies GosysOppgave`() {
        val behandling = spyk<AnkeITrygderettenbehandling> {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { nyAnkebehandlingKA } returns now
            every { gosysOppgaveId } returns 123L
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
        }
        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        behandlingAvslutningService.avsluttBehandling(behandlingId)

        verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
        verify(exactly = 1) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        verify(exactly = 1) {
            gosysOppgaveService.addKommentar(
                any(),
                "Klageinstansen har opprettet ny behandling i Kabal.",
                any(),
                any()
            )
        }
        verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
        verify(exactly = 0) { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) }
        verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
        verify(exactly = 0) { kafkaEventRepository.save(any()) }
    }

    @Test
    fun `AnkeITrygderettenbehandling creates BehandlingEtterTrygderettenOpphevet`() {
        val behandling = spyk<AnkeITrygderettenbehandling> {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { nyBehandlingEtterTROpphevet } returns now
            every { utfall } returns Utfall.OPPHEVET
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
        }
        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        behandlingAvslutningService.avsluttBehandling(behandlingId)

        verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
        verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
        verify(exactly = 1) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
        verify(exactly = 0) { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) }
        verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
        verify(exactly = 0) { kafkaEventRepository.save(any()) }
    }

    @Test
    fun `AnkeITrygderettenbehandling with gosysOppgaveId creates BehandlingEtterTrygderettenOpphevet and notifies GosysOppgave`() {
        val behandling = spyk<AnkeITrygderettenbehandling> {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { nyBehandlingEtterTROpphevet } returns now
            every { utfall } returns Utfall.OPPHEVET
            every { gosysOppgaveId } returns 123L
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
        }
        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        behandlingAvslutningService.avsluttBehandling(behandlingId)

        verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
        verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
        verify(exactly = 1) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        verify(exactly = 1) {
            gosysOppgaveService.addKommentar(
                any(),
                "Klageinstansen har opprettet ny behandling i Kabal etter at Trygderetten opphevet saken.",
                any(),
                any()
            )
        }
        verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
        verify(exactly = 0) { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) }
        verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
        verify(exactly = 0) { kafkaEventRepository.save(any()) }
    }

    @Test
    fun `Omgjoeringskravbehandling with utfall not MEDHOLD_ETTER_FVL_35 and gosysOppgaveId closes Gosys oppgave`() {
        val behandling = spyk<Omgjoeringskravbehandling> {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { utfall } returns Utfall.BESLUTNING_IKKE_OMGJOERE
            every { gosysOppgaveId } returns 123L
            //Ensured in BehandlingService
            every { ignoreGosysOppgave } returns false
            //Ensured in BehandlingService
            every { gosysOppgaveUpdate } returns null
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
        }
        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        behandlingAvslutningService.avsluttBehandling(behandlingId)

        verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
        verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
        verify(exactly = 1) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
        verify(exactly = 0) { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) }
        verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
        verify(exactly = 0) { kafkaEventRepository.save(any()) }
    }

    @Test
    fun `Omgjoeringskravbehandling with utfall not MEDHOLD_ETTER_FVL_35 does nothing external`() {
        val behandling = spyk<Omgjoeringskravbehandling> {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { utfall } returns Utfall.BESLUTNING_IKKE_OMGJOERE
            //Ensured in BehandlingService
            every { gosysOppgaveId } returns null
            //Ensured in BehandlingService
            every { ignoreGosysOppgave } returns false
            //Ensured in BehandlingService
            every { gosysOppgaveUpdate } returns null
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
        }
        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        behandlingAvslutningService.avsluttBehandling(behandlingId)

        verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
        verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
        verify(exactly = 0) { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) }
        verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
        verify(exactly = 0) { kafkaEventRepository.save(any()) }
    }

    @Test
    fun `Klagebehandling from modern fagsystem creates kafka event`() {
        val behandling = spyk<Klagebehandling> {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { gosysOppgaveId } returns null
            every { utfall } returns Utfall.STADFESTELSE
            every { fagsystem } returns Fagsystem.FS36
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
            every { kildeReferanse } returns "kildereferanse"
        }
        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        every { kafkaEventRepository.save(any()) } returns mockk()

        behandlingAvslutningService.avsluttBehandling(behandlingId)

        verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
        verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
        verify(exactly = 1) { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) }
        verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
        verify(exactly = 1) { kafkaEventRepository.save(any()) }
    }

    @Test
    fun `Klagebehandling from Infotrygd should update Infotrygd`() {
        val behandling = spyk<Klagebehandling> {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { gosysOppgaveId } returns null
            every { utfall } returns Utfall.STADFESTELSE
            every { fagsystem } returns Fagsystem.IT01
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
        }
        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        every { fssProxyClient.getSakWithAppAccess(any(), any()) } returns mockk<SakFromKlanke>(relaxed = true) {
            every { typeResultat } returns "typeResultat"
        }

        behandlingAvslutningService.avsluttBehandling(behandlingId)

        verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
        verify(exactly = 1) {
            fssProxyClient.setToFinishedWithAppAccess(
                any(), SakFinishedInput(
                    status = SakFinishedInput.Status.RETURNERT_TK,
                    nivaa = SakFinishedInput.Nivaa.KA,
                    typeResultat = SakFinishedInput.TypeResultat.RESULTAT,
                    utfall = SakFinishedInput.Utfall.AVSLAG,
                    mottaker = SakFinishedInput.Mottaker.TRYGDEKONTOR,
                    saksbehandlerIdent = "ident"
                )
            )
        }
        verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
        verify(exactly = 1) { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) }
        verify(exactly = 1) { fssProxyClient.getSakWithAppAccess(any(), any()) }
        verify(exactly = 0) { kafkaEventRepository.save(any()) }
    }

    @Test
    fun `OmgjoeringskravbehandlingBasedOnJournalpost should not update infotrygd, only Gosys`() {
        val behandling = spyk<OmgjoeringskravbehandlingBasedOnJournalpost> {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { gosysOppgaveId } returns 123L
            every { utfall } returns Utfall.MEDHOLD_ETTER_FVL_35
            every { fagsystem } returns Fagsystem.BISYS
            every { type } returns Type.OMGJOERINGSKRAV
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
        }
        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        behandlingAvslutningService.avsluttBehandling(behandlingId)

        verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
        verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
        verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
        verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
        verify(exactly = 1) { dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(any()) }
        verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
        verify(exactly = 0) { kafkaEventRepository.save(any()) }
    }
}