package no.nav.klage.oppgave.service.avslutning

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.klage.dokument.service.DokumentUnderArbeidCommonService
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFinishedInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFromKlanke
import no.nav.klage.oppgave.domain.behandling.*
import no.nav.klage.oppgave.domain.behandling.embedded.Ferdigstilling
import no.nav.klage.oppgave.domain.behandling.embedded.GosysOppgaveUpdate
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.service.*
import org.junit.jupiter.api.*
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
    private val gjenopptaksbehandlingService = mockk<GjenopptaksbehandlingService>(relaxed = true)
    private val gjenopptakITrygderettenbehandlingService =
        mockk<GjenopptakITrygderettenbehandlingService>(relaxed = true)

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
        systembrukerIdent = systembrukerIdent,
        gjenopptaksbehandlingService = gjenopptaksbehandlingService,
        gjenopptakITrygderettenbehandlingService = gjenopptakITrygderettenbehandlingService,
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
        every { kafkaEventRepository.save(any()) } returns mockk()
        every { fssProxyClient.getSakWithAppAccess(any(), any()) } returns mockk<SakFromKlanke>(relaxed = true) {
            every { typeResultat } returns "typeResultat"
        }
    }

    @AfterEach
    internal fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `Throws exception if already closed`() {
        val behandling = mockk<Klagebehandling>(relaxed = true) {
            every { ferdigstilling } returns mockk { every { avsluttet } returns now }
        }

        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        assertThrows<BehandlingAvsluttetException> {
            behandlingAvslutningService.avsluttBehandling(behandlingId)
        }
    }

    @Nested
    inner class KlagebehandlingTest {
        val behandling = mockk<Klagebehandling>(relaxed = true) {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
            every { kildeReferanse } returns "kildereferanse"
        }

        @BeforeEach
        fun before() {
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `Klagebehandling from modern fagsystem creates kafka event`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Klagebehandling from Infotrygd should update Infotrygd and GosysOppgave`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = ""
            )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
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
            verify(exactly = 1) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    inner class AnkebehandlingTest {
        val behandling = mockk<Ankebehandling>(relaxed = true) {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
            every { kildeReferanse } returns "kildereferanse"
            every { shouldBeSentToTrygderetten() } answers { callOriginal() }
        }

        @BeforeEach
        fun before() {
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `Ankebehandling with utfall going to Trygderetten and fagsystem Infotrygd creates AnkeITrygderettenbehandling and updates Infotrygd and GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = ""
            )
            every { behandling.utfall } returns Utfall.INNSTILLING_STADFESTELSE
            every { behandling.createAnkeITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 1) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
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
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Ankebehandling with utfall to Trygderetten creates AnkeITrygderettenbehandling`() {
            every { behandling.fagsystem } returns Fagsystem.FS36
            every { behandling.utfall } returns Utfall.INNSTILLING_STADFESTELSE
            every { behandling.createAnkeITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 1) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Ankebehandling with utfall not going to Trygderetten from modern fagsystem creates kafka event`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Ankebehandling with utfall not going to Trygderetten from Infotrygd should update Infotrygd and GosysOppgave`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = 0,
                oppgaveUpdateKommentar = ""
            )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
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
            verify(exactly = 1) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    inner class AnkeITrygderettenbehandlingTest {
        val behandling = mockk<AnkeITrygderettenbehandling>(relaxed = true) {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
            every { kildeReferanse } returns "kildereferanse"
            every { nyAnkebehandlingKA } returns null
            every { shouldCreateNewAnkebehandling() } answers { callOriginal() }
            every { shouldCreateNewBehandlingEtterTROpphevet() } answers { callOriginal() }
            every { shouldNotCreateNewBehandling() } answers { callOriginal() }

        }

        @BeforeEach
        fun before() {
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `AnkeITrygderettenbehandling from modernized fagsystem with utfall HENVIST creates new Ankebehandling`() {
            every { behandling.utfall } returns Utfall.HENVIST
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandling from Infotrygd with utfall HENVIST and gosysOppgaveId creates new Ankebehandling and notifies GosysOppgave`() {
            every { behandling.utfall } returns Utfall.HENVIST
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns mockk<GosysOppgaveUpdate>()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
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
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandling from modernized fagsystem tagged with nyAnkebehandlingKA creates new Ankebehandling`() {
            every { behandling.nyAnkebehandlingKA } returns now
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandling from Infotrygd tagged with nyAnkebehandlingKA and gosysOppgaveId creates new Ankebehandling and notifies GosysOppgave`() {
            every { behandling.nyAnkebehandlingKA } returns now
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
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
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandling from modernized fagsystem with utfall Opphevet and nyBehandling tag creates BehandlingEtterTrygderettenOpphevet`() {
            every { behandling.nyBehandlingEtterTROpphevet } returns now
            every { behandling.utfall } returns Utfall.OPPHEVET
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 1) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandling from Infotrygd with utfall Opphevet, gosysOppgaveId and nyBehandling tag creates BehandlingEtterTrygderettenOpphevet and notifies GosysOppgave`() {
            every { behandling.nyBehandlingEtterTROpphevet } returns now
            every { behandling.utfall } returns Utfall.OPPHEVET
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 1) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
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
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandling from modern fagsystem with utfall Opphevet but no nyBehandling tag sends Kafka event to fagsystem`() {
            every { behandling.nyBehandlingEtterTROpphevet } returns null
            every { behandling.utfall } returns Utfall.OPPHEVET
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandling from modern fagsystem with different utfall sends Kafka event to fagsystem`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandling from Infotrygd with different utfall updates Infotrygd`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = ""
            )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
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
            verify(exactly = 1) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    inner class OmgjoeringskravbehandlingTest {
        val behandling = mockk<Omgjoeringskravbehandling>(relaxed = true) {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
            every { kildeReferanse } returns "kildereferanse"
            every { shouldBeSentToVedtaksinstans() } answers { callOriginal() }
            every { shouldBeCompletedInKA() } answers { callOriginal() }
        }

        @BeforeEach
        fun before() {
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `Omgjoeringskravbehandling with utfall MEDHOLD_ETTER_FVL_35 based on Infotrygd should update Gosys`() {
            every { behandling.utfall } returns Utfall.MEDHOLD_ETTER_FVL_35
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = ""
            )
            every { behandling.fagsystem } returns Fagsystem.IT01

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Omgjoeringskravbehandling with utfall MEDHOLD_ETTER_FVL_35 based on modernized fagsystem should create Kafka event`() {
            every { behandling.utfall } returns Utfall.MEDHOLD_ETTER_FVL_35
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Omgjoeringskravbehandling with utfall not MEDHOLD_ETTER_FVL_35 and gosysOppgaveId closes Gosys oppgave`() {
            every { behandling.utfall } returns Utfall.BESLUTNING_IKKE_OMGJOERE
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            //Ensured in BehandlingService
            every { behandling.ignoreGosysOppgave } returns false
            //Ensured in BehandlingService
            every { behandling.gosysOppgaveUpdate } returns null

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Omgjoeringskravbehandling with utfall not MEDHOLD_ETTER_FVL_35 and no gosysOppgaveId does nothing external`() {
            every { behandling.utfall } returns Utfall.BESLUTNING_IKKE_OMGJOERE
            //Ensured in BehandlingService
            every { behandling.gosysOppgaveId } returns null
            //Ensured in BehandlingService
            every { behandling.ignoreGosysOppgave } returns false
            //Ensured in BehandlingService
            every { behandling.gosysOppgaveUpdate } returns null

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `OmgjoeringskravbehandlingBasedOnJournalpost should not update infotrygd, only Gosys`() {
            val behandling = mockk<OmgjoeringskravbehandlingBasedOnJournalpost>(relaxed = true) {
                every { id } returns behandlingId
                every { ferdigstilling } returns Ferdigstilling(
                    avsluttet = null,
                    avsluttetAvSaksbehandler = now,
                    navIdent = "",
                    navn = ""
                )
                every { gosysOppgaveId } returns 123L
                every { gosysOppgaveRequired } returns true
                every { gosysOppgaveUpdate } returns mockk<GosysOppgaveUpdate>()
                every { utfall } returns Utfall.MEDHOLD_ETTER_FVL_35
                every { fagsystem } returns Fagsystem.BISYS
                every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                every { shouldBeSentToVedtaksinstans() } answers { callOriginal() }
                every { shouldBeCompletedInKA() } answers { callOriginal() }
            }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

    }

    @Nested
    inner class BehandlingEtterTrygderettenOpphevetTest {
        val behandling = mockk<BehandlingEtterTrygderettenOpphevet>(relaxed = true) {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
            every { kildeReferanse } returns "kildereferanse"
        }

        @BeforeEach
        fun before() {
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `BehandlingEtterTrygderettenOpphevet from modern fagsystem creates kafka event`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `BehandlingEtterTrygderettenOpphevet from Infotrygd should update Infotrygd and GosysOppgave`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = ""
            )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
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
            verify(exactly = 1) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    inner class GjenopptaksbehandlingTest {
        val behandling = mockk<Gjenopptaksbehandling>(relaxed = true) {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
            every { kildeReferanse } returns "kildereferanse"
            every { shouldBeSentToTrygderetten() } answers { callOriginal() }
            every { shouldBeSentToVedtaksinstans() } answers { callOriginal() }
            every { shouldBeCompletedInKA() } answers { callOriginal() }
        }

        @BeforeEach
        fun before() {
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `GjenopptaksbehandlingBasedOnJournalpost with utfall MEDHOLD_ETTER_FVL_35 should update Gosys`() {
            val behandling = mockk<GjenopptaksbehandlingBasedOnJournalpost>(relaxed = true) {
                every { id } returns behandlingId
                every { ferdigstilling } returns Ferdigstilling(
                    avsluttet = null,
                    avsluttetAvSaksbehandler = now,
                    navIdent = "",
                    navn = ""
                )
                every { gosysOppgaveId } returns 123L
                every { gosysOppgaveUpdate } returns mockk<GosysOppgaveUpdate>()
                every { gosysOppgaveRequired } returns true
                every { utfall } returns Utfall.MEDHOLD_ETTER_FVL_35
                every { fagsystem } returns Fagsystem.BISYS
                every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                every { shouldBeSentToTrygderetten() } answers { callOriginal() }
                every { shouldBeSentToVedtaksinstans() } answers { callOriginal() }
                every { shouldBeCompletedInKA() } answers { callOriginal() }
            }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        @Disabled
        fun `GjenopptaksbehandlingBasedOnKabalBehandling with utfall MEDHOLD_ETTER_FVL_35 should create Kafka message`() {
            val behandling = mockk<GjenopptaksbehandlingBasedOnKabalBehandling>(relaxed = true) {
                every { id } returns behandlingId
                every { ferdigstilling } returns Ferdigstilling(
                    avsluttet = null,
                    avsluttetAvSaksbehandler = now,
                    navIdent = "",
                    navn = ""
                )
                every { gosysOppgaveRequired } returns false
                every { utfall } returns Utfall.MEDHOLD_ETTER_FVL_35
                every { fagsystem } returns Fagsystem.BISYS
                every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                every { kildeReferanse } returns "kildereferanse"
                every { shouldBeSentToTrygderetten() } answers { callOriginal() }
                every { shouldBeSentToVedtaksinstans() } answers { callOriginal() }
                every { shouldBeCompletedInKA() } answers { callOriginal() }
            }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }


        @Test
        fun `Gjenopptaksbehandling with Gosysoppgave with utfall going to Trygderetten creates GjenopptakITrygderettenbehandling and updates GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = ""
            )
            every { behandling.utfall } returns Utfall.INNSTILLING_GJENOPPTAS_KAS_VEDTAK_STADFESTES
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 1) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Gjenopptaksbehandling without Gosysoppgave with utfall going to Trygderetten creates GjenopptakITrygderettenbehandling`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.INNSTILLING_GJENOPPTAS_KAS_VEDTAK_STADFESTES
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 1) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Gjenopptaksbehandling with gosysoppgave and utfall not going to Trygderetten or vedtaksinstans updates GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = ""
            )
            every { behandling.utfall } returns Utfall.TRUKKET
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Gjenopptaksbehandling without gosysoppgave and utfall not going to Trygderetten does nothing external`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.TRUKKET
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    inner class GjenopptakITrygderettenbehandlingTest {
        val behandling = mockk<GjenopptakITrygderettenbehandling>(relaxed = true) {
            every { id } returns behandlingId
            every { ferdigstilling } returns Ferdigstilling(
                avsluttet = null,
                avsluttetAvSaksbehandler = now,
                navIdent = "",
                navn = ""
            )
            every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
            every { kildeReferanse } returns "kildereferanse"
            every { shouldCreateNewGjenopptaksbehandling() } answers { callOriginal() }
            every { shouldCreateNewBehandlingEtterTROpphevet() } answers { callOriginal() }
            every { shouldBeCompletedInKA() } answers { callOriginal() }
        }

        @BeforeEach
        fun before() {
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `GjenopptakITrygderettenbehandling without gosysoppgave and utfall not going vedtaksinstans does nothing external`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.IKKE_GJENOPPTATT
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandling with gosysoppgave and utfall not going vedtaksinstans finalizes GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveUpdate } returns GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = ""
            )
            every { behandling.utfall } returns Utfall.IKKE_GJENOPPTATT
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandling without gosysoppgave and utfall opphevet new behandling KA creates new behandling`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.GJENOPPTATT_OPPHEVET
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()
            every { behandling.nyBehandlingEtterTROpphevet } returns now

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 1) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandling with gosysoppgave and utfall opphevet and new behandling KA creates new behandling and updates GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveUpdate } returns GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = ""
            )
            every { behandling.utfall } returns Utfall.GJENOPPTATT_OPPHEVET
            every { behandling.nyBehandlingEtterTROpphevet } returns now
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 1) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 1) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        @Disabled
        fun `GjenopptakITrygderettenbehandling without gosysoppgave and utfall opphevet new behandling VL sends Kafka message`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.GJENOPPTATT_OPPHEVET
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()
            every { behandling.nyBehandlingEtterTROpphevet } returns null

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandling with gosysoppgave and utfall opphevet new behandling VL updates GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveUpdate } returns GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = ""
            )
            every { behandling.utfall } returns Utfall.GJENOPPTATT_OPPHEVET
            every { behandling.nyBehandlingEtterTROpphevet } returns null
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        @Disabled
        fun `GjenopptakITrygderettenbehandling without gosysoppgave and utfall gjenopptatt - delvis eller fullt medhold sends Kafka message`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.GJENOPPTATT_DELVIS_ELLER_FULLT_MEDHOLD
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandling with gosysoppgave and utfall gjenopptatt - delvis eller fullt medhold updates GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveUpdate } returns GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = ""
            )
            every { behandling.utfall } returns Utfall.GJENOPPTATT_DELVIS_ELLER_FULLT_MEDHOLD
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 1) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandling tagged with nyGjenopptaksbehandlingKA without gosysOppgave creates new Gjenopptaksbehandling`() {
            every { behandling.nyGjenopptaksbehandlingKA } returns now
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 1) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
            verify(exactly = 0) { gosysOppgaveService.addKommentar(any(), any(), any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(any(), any()) }
            verify(exactly = 0) { gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(any(), any(), any()) }
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandling tagged with nyAnkebehandlingKA and with gosysOppgave creates new Ankebehandling and notifies GosysOppgave`() {
            every { behandling.nyGjenopptaksbehandlingKA } returns now
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFromAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any()
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 1) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
                    any()
                )
            }
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
            verify(exactly = 0) { fssProxyClient.setToFinishedWithAppAccess(any(), any()) }
            verify(exactly = 0) { fssProxyClient.getSakWithAppAccess(any(), any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any()
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }
}