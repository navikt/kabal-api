package no.nav.klage.oppgave.service.avslutning

import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.klage.dokument.service.DokumentUnderArbeidCommonService
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.typeToUtfall
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.klagefssproxy.domain.GetSakAppAccessInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFinishedInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFromKlanke
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingEtterTrygderettenOpphevet
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.Omgjoeringskravbehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Ferdigstilling
import no.nav.klage.oppgave.domain.behandling.embedded.GosysOppgaveUpdate
import no.nav.klage.oppgave.domain.kafka.BehandlingEvent
import no.nav.klage.oppgave.domain.kafka.BehandlingEventType
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.service.AnkeITrygderettenbehandlingService
import no.nav.klage.oppgave.service.AnkebehandlingService
import no.nav.klage.oppgave.service.BehandlingEtterTrygderettenOpphevetService
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.GjenopptakITrygderettenbehandlingService
import no.nav.klage.oppgave.service.GjenopptaksbehandlingService
import no.nav.klage.oppgave.service.GosysOppgaveService
import no.nav.klage.oppgave.service.KlankeService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.ApplicationEventPublisher
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.util.UUID

class BehandlingAvslutningServiceTest {
    private val behandlingService = mockk<BehandlingService>()
    private val ankeITrygderettenbehandlingService = mockk<AnkeITrygderettenbehandlingService>(relaxed = true)
    private val behandlingEtterTrygderettenOpphevetService =
        mockk<BehandlingEtterTrygderettenOpphevetService>(relaxed = true)
    private val ankebehandlingService = mockk<AnkebehandlingService>(relaxed = true)
    private val klankeService = mockk<KlankeService>(relaxed = true)
    private val gosysOppgaveService = mockk<GosysOppgaveService>(relaxed = true)
    private val kafkaEventRepository = mockk<KafkaEventRepository>(relaxed = true)
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val systembrukerIdent = "system"
    private val dokumentUnderArbeidCommonService = mockk<DokumentUnderArbeidCommonService>(relaxed = true)
    private val gjenopptaksbehandlingService = mockk<GjenopptaksbehandlingService>(relaxed = true)
    private val gjenopptakITrygderettenbehandlingService =
        mockk<GjenopptakITrygderettenbehandlingService>(relaxed = true)

    private val behandlingAvslutningService =
        BehandlingAvslutningService(
            kafkaEventRepository = kafkaEventRepository,
            behandlingService = behandlingService,
            applicationEventPublisher = applicationEventPublisher,
            dokumentUnderArbeidCommonService = dokumentUnderArbeidCommonService,
            ankeITrygderettenbehandlingService = ankeITrygderettenbehandlingService,
            behandlingEtterTrygderettenOpphevetService = behandlingEtterTrygderettenOpphevetService,
            ankebehandlingService = ankebehandlingService,
            klankeService = klankeService,
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
                any(),
            )
        } returns emptySet()
        every { kafkaEventRepository.save(any()) } returns mockk()
        every { klankeService.getSakWithAppAccess(sakId = any(), input = any()) } returns
            mockk<SakFromKlanke>(relaxed = true) {
                every { typeResultat } returns "typeResultat"
            }
    }

    @AfterEach
    internal fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `Throws exception if already closed`() {
        val behandling =
            mockk<Klagebehandling>(relaxed = true) {
                every { ferdigstilling } returns mockk { every { avsluttet } returns now }
            }

        every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

        assertThrows<BehandlingAvsluttetException> {
            behandlingAvslutningService.avsluttBehandling(behandlingId)
        }
    }

    @Nested
    inner class KlagebehandlingTest {
        val behandling =
            mockk<Klagebehandling>(relaxed = true) {
                every { id } returns behandlingId
                every { ferdigstilling } returns
                    Ferdigstilling(
                        avsluttet = null,
                        avsluttetAvSaksbehandler = now,
                        navIdent = "",
                        navn = "",
                    )
                every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                every { kildeReferanse } returns "kildereferanse"
            }

        @BeforeEach
        fun before() {
            every { behandling.shouldNotifyVedtaksinstans() } answers { callOriginal() }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `Klagebehandling from modern fagsystem creates kafka event`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
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
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 1) {
                klankeService.setToFinishedWithAppAccess(
                    sakId = any(),
                    input =
                        SakFinishedInput(
                            status = SakFinishedInput.Status.RETURNERT_TK,
                            nivaa = SakFinishedInput.Nivaa.KA,
                            typeResultat = SakFinishedInput.TypeResultat.RESULTAT,
                            utfall = SakFinishedInput.Utfall.AVSLAG,
                            mottaker = SakFinishedInput.Mottaker.TRYGDEKONTOR,
                            saksbehandlerIdent = "ident",
                        ),
                )
            }
            verify(exactly = 1) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    inner class AnkebehandlingTest {
        val behandling =
            mockk<AnkebehandlingFoer2027>(relaxed = true) {
                every { id } returns behandlingId
                every { ferdigstilling } returns
                    Ferdigstilling(
                        avsluttet = null,
                        avsluttetAvSaksbehandler = now,
                        navIdent = "",
                        navn = "",
                    )
                every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                every { kildeReferanse } returns "kildereferanse"
                every { shouldBeSentToTrygderetten() } answers { callOriginal() }
            }

        @BeforeEach
        fun before() {
            every { behandling.shouldNotifyVedtaksinstans() } answers { callOriginal() }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `AnkebehandlingFoer2027 with utfall going to Trygderetten and fagsystem Infotrygd creates AnkeITrygderettenbehandlingFoer2027 and updates Infotrygd and GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )
            every { behandling.utfall } returns Utfall.INNSTILLING_STADFESTELSE
            every { behandling.createAnkeITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 1) {
                klankeService.setToFinishedWithAppAccess(
                    sakId = any(),
                    input =
                        SakFinishedInput(
                            status = SakFinishedInput.Status.VIDERESENDT_TR,
                            nivaa = SakFinishedInput.Nivaa.KA,
                            typeResultat = SakFinishedInput.TypeResultat.INNSTILLING_2,
                            utfall = SakFinishedInput.Utfall.AVSLAG,
                            mottaker = SakFinishedInput.Mottaker.TRYGDERETTEN,
                            saksbehandlerIdent = "ident",
                        ),
                )
            }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkebehandlingFoer2027 with utfall to Trygderetten creates AnkeITrygderettenbehandlingFoer2027`() {
            every { behandling.fagsystem } returns Fagsystem.FS36
            every { behandling.utfall } returns Utfall.INNSTILLING_STADFESTELSE
            every { behandling.createAnkeITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkebehandlingFoer2027 with utfall not going to Trygderetten from modern fagsystem creates kafka event`() {
            every { behandling.utfall } returns Utfall.MEDHOLD
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkebehandlingFoer2027 with utfall not going to Trygderetten from Infotrygd should update Infotrygd and GosysOppgave`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = 0,
                    oppgaveUpdateKommentar = "",
                )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 1) {
                klankeService.setToFinishedWithAppAccess(
                    sakId = any(),
                    input =
                        SakFinishedInput(
                            status = SakFinishedInput.Status.RETURNERT_TK,
                            nivaa = SakFinishedInput.Nivaa.KA,
                            typeResultat = SakFinishedInput.TypeResultat.RESULTAT,
                            utfall = SakFinishedInput.Utfall.AVSLAG,
                            mottaker = SakFinishedInput.Mottaker.TRYGDEKONTOR,
                            saksbehandlerIdent = "ident",
                        ),
                )
            }
            verify(exactly = 1) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkebehandlingFoer2027 with utfall going to Trygderetten from Infotrygd with ytelse BAR_BAR creates AnkeITrygderettenbehandlingFoer2027 without updating Infotrygd`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.ytelse } returns Ytelse.BAR_BAR
            every { behandling.type } returns Type.ANKE_FOER_2027
            every { behandling.isSakNoLongerInInfotrygd() } answers { callOriginal() }
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )
            every { behandling.utfall } returns Utfall.INNSTILLING_STADFESTELSE
            every { behandling.createAnkeITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkebehandlingFoer2027 with utfall not going to Trygderetten from Infotrygd with ytelse BAR_BAR does not update Infotrygd, only GosysOppgave`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.ytelse } returns Ytelse.BAR_BAR
            every { behandling.type } returns Type.ANKE_FOER_2027
            every { behandling.isSakNoLongerInInfotrygd() } answers { callOriginal() }
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = 0,
                    oppgaveUpdateKommentar = "",
                )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    inner class AnkebehandlingEtter2027Test {
        val behandling =
            mockk<AnkebehandlingEtter2027>(relaxed = true) {
                every { id } returns behandlingId
                every { ferdigstilling } returns
                    Ferdigstilling(
                        avsluttet = null,
                        avsluttetAvSaksbehandler = now,
                        navIdent = "",
                        navn = "",
                    )
                every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                every { kildeReferanse } returns "kildereferanse"
                every { shouldBeSentToTrygderetten() } answers { callOriginal() }
            }

        @BeforeEach
        fun before() {
            every { behandling.shouldNotifyVedtaksinstans() } answers { callOriginal() }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `AnkebehandlingEtter2027 from Infotrygd sent to Trygderetten updates Infotrygd and creates AnkeITrygderettenEtter2027`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.utfall } returns Utfall.INNSTILLING_STADFESTELSE
            val followOnInput = mockk<no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027Input>()
            every { behandling.createAnkeITrygderettenbehandlingEtter2027Input() } returns followOnInput

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) {
                ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingEtter2027(followOnInput)
            }
            verify(exactly = 1) {
                klankeService.setToFinishedWithAppAccess(
                    sakId = "kildereferanse",
                    input =
                        SakFinishedInput(
                            status = SakFinishedInput.Status.VIDERESENDT_TR,
                            nivaa = SakFinishedInput.Nivaa.KA,
                            typeResultat = SakFinishedInput.TypeResultat.INNSTILLING_2,
                            utfall = SakFinishedInput.Utfall.AVSLAG,
                            mottaker = SakFinishedInput.Mottaker.TRYGDERETTEN,
                            saksbehandlerIdent = "ident",
                        ),
                )
            }
        }

        @Test
        fun `AnkebehandlingEtter2027 from modern fagsystem sent to Trygderetten does not notify Infotrygd and creates AnkeITrygderettenEtter2027`() {
            every { behandling.fagsystem } returns Fagsystem.FS36
            every { behandling.utfall } returns Utfall.INNSTILLING_STADFESTELSE
            val followOnInput = mockk<no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027Input>()
            every { behandling.createAnkeITrygderettenbehandlingEtter2027Input() } returns followOnInput

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) {
                ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingEtter2027(followOnInput)
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
        }

        @Test
        fun `AnkebehandlingEtter2027 from modern fagsystem not sent to Trygderetten creates AnkeITrygderettenEtter2027 and kafka event`() {
            every { behandling.fagsystem } returns Fagsystem.FS36
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.MEDHOLD
            val followOnInput = mockk<no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027Input>()
            every { behandling.createAnkeITrygderettenbehandlingEtter2027Input() } returns followOnInput
            val savedKafkaEvent = slot<KafkaEvent>()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) {
                ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingEtter2027(followOnInput)
            }
            verify(exactly = 1) { kafkaEventRepository.save(capture(savedKafkaEvent)) }
            val event = jacksonObjectMapper().readValue(savedKafkaEvent.captured.jsonPayload, BehandlingEvent::class.java)
            assertEquals(BehandlingEventType.ANKEBEHANDLING_AVSLUTTET, event.type)
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
        }

        @Test
        fun `AnkebehandlingEtter2027 with Gosys task not sent to Trygderetten creates AnkeITrygderettenEtter2027 and updates Gosys`() {
            every { behandling.fagsystem } returns Fagsystem.FS36
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveUpdate } returns mockk<GosysOppgaveUpdate>()
            val followOnInput = mockk<no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027Input>()
            every { behandling.createAnkeITrygderettenbehandlingEtter2027Input() } returns followOnInput

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) {
                ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingEtter2027(followOnInput)
            }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = behandling,
                    systemContext = true,
                    throwExceptionIfFerdigstilt = true,
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
        }
    }

    @Nested
    inner class AnkeITrygderettenbehandlingTest {
        val behandling =
            mockk<AnkeITrygderettenbehandlingFoer2027>(relaxed = true) {
                every { id } returns behandlingId
                every { ferdigstilling } returns
                    Ferdigstilling(
                        avsluttet = null,
                        avsluttetAvSaksbehandler = now,
                        navIdent = "",
                        navn = "",
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
            every { behandling.shouldNotifyVedtaksinstans() } answers { callOriginal() }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `AnkeITrygderettenbehandlingFoer2027 from modernized fagsystem with utfall HENVIST creates new AnkebehandlingFoer2027`() {
            every { behandling.utfall } returns Utfall.HENVIST
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingFoer2027 from Infotrygd with utfall HENVIST and gosysOppgaveId creates new AnkebehandlingFoer2027 and notifies GosysOppgave`() {
            every { behandling.utfall } returns Utfall.HENVIST
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns mockk<GosysOppgaveUpdate>()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 1) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = "Klageinstansen har opprettet ny behandling i Kabal etter at Trygderetten har henvist saken.",
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingFoer2027 from modernized fagsystem tagged with nyAnkebehandlingKA creates new AnkebehandlingFoer2027`() {
            every { behandling.nyAnkebehandlingKA } returns now
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingFoer2027 from Infotrygd tagged with nyAnkebehandlingKA and gosysOppgaveId creates new AnkebehandlingFoer2027 and notifies GosysOppgave`() {
            every { behandling.nyAnkebehandlingKA } returns now
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 1) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = "Klageinstansen har opprettet ny behandling i Kabal.",
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingFoer2027 from modernized fagsystem with utfall Opphevet and nyBehandling tag creates BehandlingEtterTrygderettenOpphevet`() {
            every { behandling.nyBehandlingEtterTROpphevet } returns now
            every { behandling.utfall } returns Utfall.OPPHEVET
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingFoer2027 from Infotrygd with utfall Opphevet, gosysOppgaveId and nyBehandling tag creates BehandlingEtterTrygderettenOpphevet and notifies GosysOppgave`() {
            every { behandling.nyBehandlingEtterTROpphevet } returns now
            every { behandling.utfall } returns Utfall.OPPHEVET
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 1) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = "Klageinstansen har opprettet ny behandling i Kabal etter at Trygderetten opphevet saken.",
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingFoer2027 from modern fagsystem with utfall Opphevet but no nyBehandling tag sends Kafka event to fagsystem`() {
            every { behandling.nyBehandlingEtterTROpphevet } returns null
            every { behandling.utfall } returns Utfall.OPPHEVET
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingFoer2027 from Infotrygd with different utfall updates Infotrygd`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 1) {
                klankeService.setToFinishedWithAppAccess(
                    sakId = any(),
                    input =
                        SakFinishedInput(
                            status = SakFinishedInput.Status.RETURNERT_TK,
                            nivaa = SakFinishedInput.Nivaa.TR,
                            typeResultat = SakFinishedInput.TypeResultat.RESULTAT,
                            utfall = SakFinishedInput.Utfall.AVSLAG,
                            mottaker = SakFinishedInput.Mottaker.TRYGDEKONTOR,
                            saksbehandlerIdent = "ident",
                        ),
                )
            }
            verify(exactly = 1) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingFoer2027 from Infotrygd with ytelse BAR_BAR does not update Infotrygd, only GosysOppgave`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.ytelse } returns Ytelse.BAR_BAR
            every { behandling.type } returns Type.ANKE_I_TRYGDERETTEN_FOER_2027
            every { behandling.isSakNoLongerInInfotrygd() } answers { callOriginal() }
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    inner class AnkeITrygderettenbehandlingEtter2027Test {
        val behandling =
            mockk<AnkeITrygderettenbehandlingEtter2027>(relaxed = true) {
                every { id } returns behandlingId
                every { ferdigstilling } returns
                    Ferdigstilling(
                        avsluttet = null,
                        avsluttetAvSaksbehandler = now,
                        navIdent = "",
                        navn = "",
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
            every { behandling.shouldNotifyVedtaksinstans() } answers { callOriginal() }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `AnkeITrygderettenbehandlingEtter2027 from modernized fagsystem with utfall HENVIST creates new AnkebehandlingEtter2027`() {
            every { behandling.utfall } returns Utfall.HENVIST
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankebehandlingService.createAnkebehandlingEtter2027FromAnkeITrygderettenbehandlingEtter2027(any()) }
            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingEtter2027 from Infotrygd with utfall HENVIST and gosysOppgaveId creates new AnkebehandlingEtter2027 and notifies GosysOppgave`() {
            every { behandling.utfall } returns Utfall.HENVIST
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns mockk<GosysOppgaveUpdate>()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankebehandlingService.createAnkebehandlingEtter2027FromAnkeITrygderettenbehandlingEtter2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 1) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = "Klageinstansen har opprettet ny behandling i Kabal etter at Trygderetten har henvist saken.",
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingEtter2027 from modernized fagsystem tagged with nyAnkebehandlingKA creates new AnkebehandlingEtter2027`() {
            every { behandling.nyAnkebehandlingKA } returns now
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankebehandlingService.createAnkebehandlingEtter2027FromAnkeITrygderettenbehandlingEtter2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingEtter2027 from Infotrygd tagged with nyAnkebehandlingKA and gosysOppgaveId creates new AnkebehandlingEtter2027 and notifies GosysOppgave`() {
            every { behandling.nyAnkebehandlingKA } returns now
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankebehandlingService.createAnkebehandlingEtter2027FromAnkeITrygderettenbehandlingEtter2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 1) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = "Klageinstansen har opprettet ny behandling i Kabal.",
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingEtter2027 from modernized fagsystem with utfall Opphevet and nyBehandling tag creates BehandlingEtterTrygderettenOpphevet`() {
            every { behandling.nyBehandlingEtterTROpphevet } returns now
            every { behandling.utfall } returns Utfall.OPPHEVET
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingEtter2027FromAnkeITrygderettenbehandlingEtter2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingEtter2027 from Infotrygd with utfall Opphevet, gosysOppgaveId and nyBehandling tag creates BehandlingEtterTrygderettenOpphevet and notifies GosysOppgave`() {
            every { behandling.nyBehandlingEtterTROpphevet } returns now
            every { behandling.utfall } returns Utfall.OPPHEVET
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingEtter2027FromAnkeITrygderettenbehandlingEtter2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 1) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = "Klageinstansen har opprettet ny behandling i Kabal etter at Trygderetten opphevet saken.",
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingEtter2027 from modern fagsystem with utfall Opphevet but no nyBehandling tag sends Kafka event to fagsystem`() {
            every { behandling.nyBehandlingEtterTROpphevet } returns null
            every { behandling.utfall } returns Utfall.OPPHEVET
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingEtter2027FromAnkeITrygderettenbehandlingEtter2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingEtter2027 from Infotrygd with different utfall finishes in Infotrygd and updates GosysOppgave`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingEtter2027FromAnkeITrygderettenbehandlingEtter2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 1) {
                klankeService.getSakWithAppAccess(
                    sakId = "kildereferanse",
                    input = GetSakAppAccessInput(saksbehandlerIdent = "ident"),
                )
            }
            verify(exactly = 1) {
                klankeService.setToFinishedWithAppAccess(
                    sakId = "kildereferanse",
                    input =
                        SakFinishedInput(
                            status = SakFinishedInput.Status.RETURNERT_TK,
                            nivaa = SakFinishedInput.Nivaa.TR,
                            typeResultat = SakFinishedInput.TypeResultat.RESULTAT,
                            utfall = SakFinishedInput.Utfall.AVSLAG,
                            mottaker = SakFinishedInput.Mottaker.TRYGDEKONTOR,
                            saksbehandlerIdent = "ident",
                        ),
                )
            }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingEtter2027 from modern fagsystem requiring GosysOppgave goes back to vedtaksinstans without Kafka event`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.FS36
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingEtter2027FromAnkeITrygderettenbehandlingEtter2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkeITrygderettenbehandlingEtter2027 from modern fagsystem is emitted as ANKEBEHANDLING_AVSLUTTET`() {
            every { behandling.fagsystem } returns Fagsystem.FS36
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.MEDHOLD
            val savedKafkaEvent = slot<KafkaEvent>()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { kafkaEventRepository.save(capture(savedKafkaEvent)) }
            val event = jacksonObjectMapper().readValue(savedKafkaEvent.captured.jsonPayload, BehandlingEvent::class.java)
            assertEquals(BehandlingEventType.ANKEBEHANDLING_AVSLUTTET, event.type)
            assertEquals("kildereferanse", event.kildeReferanse)
            assertEquals(behandlingId.toString(), event.kabalReferanse)
        }
    }

    @Nested
    inner class OmgjoeringskravbehandlingTest {
        val behandling =
            mockk<Omgjoeringskravbehandling>(relaxed = true) {
                every { id } returns behandlingId
                every { ferdigstilling } returns
                    Ferdigstilling(
                        avsluttet = null,
                        avsluttetAvSaksbehandler = now,
                        navIdent = "",
                        navn = "",
                    )
                every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                every { kildeReferanse } returns "kildereferanse"
                every { shouldBeSentToVedtaksinstans() } answers { callOriginal() }
                every { shouldBeCompletedInKA() } answers { callOriginal() }
            }

        @BeforeEach
        fun before() {
            every { behandling.shouldNotifyVedtaksinstans() } answers { callOriginal() }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `Omgjoeringskravbehandling with utfall MEDHOLD_ETTER_FVL_35 based on Infotrygd should update Gosys`() {
            every { behandling.utfall } returns Utfall.MEDHOLD_ETTER_FVL_35
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )
            every { behandling.fagsystem } returns Fagsystem.IT01

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Omgjoeringskravbehandling with utfall MEDHOLD_ETTER_FVL_35 based on modernized fagsystem should create Kafka event`() {
            every { behandling.utfall } returns Utfall.MEDHOLD_ETTER_FVL_35
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
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
            // Ensured in BehandlingService
            every { behandling.ignoreGosysOppgave } returns false
            // Ensured in BehandlingService
            every { behandling.gosysOppgaveUpdate } returns null

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 1) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Omgjoeringskravbehandling with utfall not MEDHOLD_ETTER_FVL_35 and no gosysOppgaveId does nothing external`() {
            every { behandling.utfall } returns Utfall.BESLUTNING_IKKE_OMGJOERE
            // Ensured in BehandlingService
            every { behandling.gosysOppgaveId } returns null
            // Ensured in BehandlingService
            every { behandling.ignoreGosysOppgave } returns false
            // Ensured in BehandlingService
            every { behandling.gosysOppgaveUpdate } returns null

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Omgjoeringskravbehandling based on journalpost should not update infotrygd, only Gosys`() {
            val behandling =
                mockk<Omgjoeringskravbehandling>(relaxed = true) {
                    every { id } returns behandlingId
                    every { ferdigstilling } returns
                        Ferdigstilling(
                            avsluttet = null,
                            avsluttetAvSaksbehandler = now,
                            navIdent = "",
                            navn = "",
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

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    inner class BehandlingEtterTrygderettenOpphevetTest {
        val behandling =
            mockk<BehandlingEtterTrygderettenOpphevet>(relaxed = true) {
                every { id } returns behandlingId
                every { ferdigstilling } returns
                    Ferdigstilling(
                        avsluttet = null,
                        avsluttetAvSaksbehandler = now,
                        navIdent = "",
                        navn = "",
                    )
                every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                every { kildeReferanse } returns "kildereferanse"
            }

        @BeforeEach
        fun before() {
            every { behandling.shouldNotifyVedtaksinstans() } answers { callOriginal() }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `BehandlingEtterTrygderettenOpphevet from modern fagsystem creates kafka event`() {
            every { behandling.utfall } returns Utfall.MEDHOLD
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
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
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 1) {
                klankeService.setToFinishedWithAppAccess(
                    sakId = any(),
                    input =
                        SakFinishedInput(
                            status = SakFinishedInput.Status.RETURNERT_TK,
                            nivaa = SakFinishedInput.Nivaa.KA,
                            typeResultat = SakFinishedInput.TypeResultat.RESULTAT,
                            utfall = SakFinishedInput.Utfall.AVSLAG,
                            mottaker = SakFinishedInput.Mottaker.TRYGDEKONTOR,
                            saksbehandlerIdent = "ident",
                        ),
                )
            }
            verify(exactly = 1) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `BehandlingEtterTrygderettenOpphevet from Infotrygd with ytelse BAR_BAR does not update Infotrygd, only GosysOppgave`() {
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.ytelse } returns Ytelse.BAR_BAR
            every { behandling.type } returns Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET
            every { behandling.isSakNoLongerInInfotrygd() } answers { callOriginal() }
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    inner class GjenopptaksbehandlingFoer2027Test {
        val behandling =
            mockk<GjenopptaksbehandlingFoer2027>(relaxed = true) {
                every { id } returns behandlingId
                every { ferdigstilling } returns
                    Ferdigstilling(
                        avsluttet = null,
                        avsluttetAvSaksbehandler = now,
                        navIdent = "",
                        navn = "",
                    )
                every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                every { kildeReferanse } returns "kildereferanse"
                every { shouldBeSentToTrygderetten() } answers { callOriginal() }
                every { shouldBeSentToVedtaksinstans() } answers { callOriginal() }
                every { shouldBeCompletedInKA() } answers { callOriginal() }
            }

        @BeforeEach
        fun before() {
            every { behandling.shouldNotifyVedtaksinstans() } answers { callOriginal() }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `GjenopptaksbehandlingFoer2027 based on journalpost with utfall MEDHOLD_ETTER_FVL_35 should update Gosys`() {
            val behandling =
                mockk<GjenopptaksbehandlingFoer2027>(relaxed = true) {
                    every { id } returns behandlingId
                    every { ferdigstilling } returns
                        Ferdigstilling(
                            avsluttet = null,
                            avsluttetAvSaksbehandler = now,
                            navIdent = "",
                            navn = "",
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

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptaksbehandlingFoer2027 with utfall MEDHOLD_ETTER_FVL_35 should create Kafka message`() {
            val behandling =
                mockk<GjenopptaksbehandlingFoer2027>(relaxed = true) {
                    every { id } returns behandlingId
                    every { ferdigstilling } returns
                        Ferdigstilling(
                            avsluttet = null,
                            avsluttetAvSaksbehandler = now,
                            navIdent = "",
                            navn = "",
                        )
                    every { gosysOppgaveRequired } returns false
                    every { utfall } returns Utfall.MEDHOLD_ETTER_FVL_35
                    every { fagsystem } returns Fagsystem.BISYS
                    every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                    every { kildeReferanse } returns "kildereferanse"
                    every { shouldBeSentToTrygderetten() } answers { callOriginal() }
                    every { shouldBeSentToVedtaksinstans() } answers { callOriginal() }
                    every { shouldBeCompletedInKA() } answers { callOriginal() }
                    every { shouldNotifyVedtaksinstans() } answers { callOriginal() }
                }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptaksbehandlingFoer2027 with Gosysoppgave with utfall going to Trygderetten creates GjenopptakITrygderettenbehandling and updates GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )
            every { behandling.utfall } returns Utfall.INNSTILLING_GJENOPPTAS_KAS_VEDTAK_STADFESTES
            every { behandling.createGjenopptakITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 1) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptaksbehandlingFoer2027 without Gosysoppgave with utfall going to Trygderetten creates GjenopptakITrygderettenbehandling`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.INNSTILLING_GJENOPPTAS_KAS_VEDTAK_STADFESTES
            every { behandling.createGjenopptakITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 1) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptaksbehandlingFoer2027 with gosysoppgave and utfall not going to Trygderetten or vedtaksinstans updates GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )
            every { behandling.utfall } returns Utfall.TRUKKET
            every { behandling.createGjenopptakITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 1) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptaksbehandlingFoer2027 without gosysoppgave and utfall not going to Trygderetten does nothing external`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.TRUKKET
            every { behandling.createGjenopptakITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    inner class GjenopptakITrygderettenbehandlingFoer2027Test {
        val behandling =
            mockk<GjenopptakITrygderettenbehandlingFoer2027>(relaxed = true) {
                every { id } returns behandlingId
                every { ferdigstilling } returns
                    Ferdigstilling(
                        avsluttet = null,
                        avsluttetAvSaksbehandler = now,
                        navIdent = "",
                        navn = "",
                    )
                every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                every { kildeReferanse } returns "kildereferanse"
                every { nyGjenopptaksbehandlingKA } returns null
                every { nyBehandlingEtterTROpphevet } returns null
                every { shouldCreateNewGjenopptaksbehandling() } answers { callOriginal() }
                every { shouldCreateNewBehandlingEtterTROpphevet() } answers { callOriginal() }
                every { shouldBeCompletedInKA() } answers { callOriginal() }
            }

        @BeforeEach
        fun before() {
            every { behandling.shouldNotifyVedtaksinstans() } answers { callOriginal() }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        @Test
        fun `GjenopptakITrygderettenbehandlingFoer2027 without gosysoppgave and utfall not going vedtaksinstans does nothing external`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.IKKE_GJENOPPTATT
            every { behandling.createGjenopptakITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandlingFoer2027 with gosysoppgave and utfall not going vedtaksinstans finalizes GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )
            every { behandling.utfall } returns Utfall.IKKE_GJENOPPTATT
            every { behandling.createGjenopptakITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 1) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandlingFoer2027 without gosysoppgave and utfall opphevet new behandling KA creates new behandling`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.GJENOPPTATT_OPPHEVET
            every { behandling.createGjenopptakITrygderettenbehandlingFoer2027Input() } returns mockk()
            every { behandling.nyBehandlingEtterTROpphevet } returns now

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandlingFoer2027 with gosysoppgave and utfall opphevet and new behandling KA creates new behandling and updates GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )
            every { behandling.utfall } returns Utfall.GJENOPPTATT_OPPHEVET
            every { behandling.nyBehandlingEtterTROpphevet } returns now
            every { behandling.createGjenopptakITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 1) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandlingFoer2027 without gosysoppgave and utfall opphevet new behandling VL sends Kafka message`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.GJENOPPTATT_OPPHEVET
            every { behandling.createGjenopptakITrygderettenbehandlingFoer2027Input() } returns mockk()
            every { behandling.nyBehandlingEtterTROpphevet } returns null

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandlingFoer2027 with gosysoppgave and utfall opphevet new behandling VL updates GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )
            every { behandling.utfall } returns Utfall.GJENOPPTATT_OPPHEVET
            every { behandling.nyBehandlingEtterTROpphevet } returns null
            every { behandling.createGjenopptakITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandlingFoer2027 without gosysoppgave and utfall gjenopptatt - delvis eller fullt medhold sends Kafka message`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.GJENOPPTATT_DELVIS_ELLER_FULLT_MEDHOLD
            every { behandling.createGjenopptakITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 1) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandlingFoer2027 with gosysoppgave and utfall gjenopptatt - delvis eller fullt medhold updates GosysOppgave`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveUpdate } returns
                GosysOppgaveUpdate(
                    oppgaveUpdateTildeltEnhetsnummer = "123",
                    oppgaveUpdateMappeId = null,
                    oppgaveUpdateKommentar = "",
                )
            every { behandling.utfall } returns Utfall.GJENOPPTATT_DELVIS_ELLER_FULLT_MEDHOLD
            every { behandling.createGjenopptakITrygderettenbehandlingFoer2027Input() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandlingFoer2027 tagged with nyGjenopptaksbehandlingKA without gosysOppgave creates new Gjenopptaksbehandling`() {
            every { behandling.nyGjenopptaksbehandlingKA } returns now
            every { behandling.fagsystem } returns Fagsystem.FS36

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `GjenopptakITrygderettenbehandlingFoer2027 tagged with nyAnkebehandlingKA and with gosysOppgave creates new AnkebehandlingFoer2027 and notifies GosysOppgave`() {
            every { behandling.nyGjenopptaksbehandlingKA } returns now
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveRequired } returns true

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(
                    any(),
                )
            }
            verify(exactly = 1) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = "Klageinstansen har opprettet ny behandling i Kabal.",
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { klankeService.getSakWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) {
                dokumentUnderArbeidCommonService.findHoveddokumenterByBehandlingIdAndHasJournalposter(
                    any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class GjenopptaksbehandlingEtter2027Test {
        private lateinit var behandling: GjenopptaksbehandlingEtter2027

        @BeforeEach
        fun before() {
            behandling =
                mockk<GjenopptaksbehandlingEtter2027>(relaxed = true) {
                    every { id } returns behandlingId
                    every { type } returns Type.BEGJAERING_OM_GJENOPPTAK_ETTER_2027
                    every { ferdigstilling } returns
                        Ferdigstilling(
                            avsluttet = null,
                            avsluttetAvSaksbehandler = now,
                            navIdent = "",
                            navn = "",
                        )
                    every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                    every { fagsystem } returns Fagsystem.FS36
                    every { kildeReferanse } returns "kildereferanse"
                    every { shouldBeSentToTrygderetten() } answers { callOriginal() }
                    every { shouldBeSentToVedtaksinstans() } answers { callOriginal() }
                    every { shouldBeCompletedInKA() } answers { callOriginal() }
                    every { shouldNotifyVedtaksinstans() } answers { callOriginal() }
                    every { createGjenopptakITrygderettenbehandlingEtter2027Input() } returns mockk()
                }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        fun utfallCases(): List<Arguments> =
            listOf(
                Arguments.of(Utfall.MEDHOLD_ETTER_FVL_35, true),
                Arguments.of(Utfall.INNSTILLING_GJENOPPTAS_KAS_VEDTAK_STADFESTES, false),
                Arguments.of(Utfall.INNSTILLING_GJENOPPTAS_IKKE, false),
                Arguments.of(Utfall.INNSTILLING_AVVIST, false),
                Arguments.of(Utfall.UGUNST, true),
            )

        @ParameterizedTest(name = "{0}: Kafka event expected = {1}")
        @MethodSource("utfallCases")
        fun `creates Trygderetten treatment for every outcome and only notifies vedtaksinstans when needed`(
            outcome: Utfall,
            expectedKafkaEvent: Boolean,
        ) {
            every { behandling.utfall } returns outcome
            every { behandling.gosysOppgaveRequired } returns false

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verifyTrygderettenTreatmentCreated()
            verify { gosysOppgaveService wasNot Called }
            verifyGjenopptakKafkaEvent(expectedKafkaEvent)
        }

        @ParameterizedTest(name = "{0}: ignored Gosys task = {1}")
        @MethodSource("gosysCases")
        fun `creates Trygderetten treatment and updates Gosys unless ignored without Kafka events`(
            outcome: Utfall,
            ignoreGosysOppgave: Boolean,
        ) {
            every { behandling.utfall } returns outcome
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveUpdate } returns mockk<GosysOppgaveUpdate>()
            every { behandling.ignoreGosysOppgave } returns ignoreGosysOppgave

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verifyTrygderettenTreatmentCreated()
            verify(exactly = if (ignoreGosysOppgave) 0 else 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = behandling,
                    systemContext = true,
                    throwExceptionIfFerdigstilt = true,
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        fun gosysCases(): List<Arguments> =
            typeToUtfall.getValue(Type.BEGJAERING_OM_GJENOPPTAK_ETTER_2027).flatMap { outcome ->
                listOf(false, true).map { ignoreGosysOppgave ->
                    Arguments.of(outcome, ignoreGosysOppgave)
                }
            }

        private fun verifyTrygderettenTreatmentCreated() {
            verify(exactly = 1) {
                gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingEtter2027(any())
            }
            verify(exactly = 0) {
                gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any())
            }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingEtter2027FromGjenopptakITrygderettenbehandlingEtter2027(any())
            }
            verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class GjenopptakITrygderettenbehandlingEtter2027Test {
        private lateinit var behandling: GjenopptakITrygderettenbehandlingEtter2027

        @BeforeEach
        fun before() {
            behandling =
                mockk<GjenopptakITrygderettenbehandlingEtter2027>(relaxed = true) {
                    every { id } returns behandlingId
                    every { type } returns Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_ETTER_2027
                    every { ferdigstilling } returns
                        Ferdigstilling(
                            avsluttet = null,
                            avsluttetAvSaksbehandler = now,
                            navIdent = "",
                            navn = "",
                        )
                    every { tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
                    every { fagsystem } returns Fagsystem.FS36
                    every { kildeReferanse } returns "kildereferanse"
                    every { nyGjenopptaksbehandlingKA } returns null
                    every { nyBehandlingEtterTROpphevet } returns null
                    every { shouldCreateNewGjenopptaksbehandling() } answers { callOriginal() }
                    every { shouldCreateNewBehandlingEtterTROpphevet() } answers { callOriginal() }
                    every { shouldBeCompletedInKA() } answers { callOriginal() }
                    every { shouldNotifyVedtaksinstans() } answers { callOriginal() }
                }
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
        }

        fun utfallCases(): List<Arguments> =
            listOf(
                Arguments.of(Utfall.GJENOPPTATT_DELVIS_ELLER_FULLT_MEDHOLD, true),
                Arguments.of(Utfall.GJENOPPTATT_OPPHEVET, true),
                Arguments.of(Utfall.GJENOPPTATT_STADFESTET, false),
                Arguments.of(Utfall.IKKE_GJENOPPTATT, false),
                Arguments.of(Utfall.AVVIST, false),
                Arguments.of(Utfall.HEVET, false),
            )

        @ParameterizedTest(name = "{0}: Kafka event expected = {1}")
        @MethodSource("utfallCases")
        fun `only notifies vedtaksinstans for outcomes requiring it without creating new treatments`(
            outcome: Utfall,
            expectedKafkaEvent: Boolean,
        ) {
            every { behandling.utfall } returns outcome
            every { behandling.gosysOppgaveRequired } returns false

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verifyNoNewTreatments()
            verify { gosysOppgaveService wasNot Called }
            verifyGjenopptakKafkaEvent(expectedKafkaEvent)
        }

        @ParameterizedTest(name = "{0}: Gosys update expected = {1}")
        @MethodSource("utfallCases")
        fun `updates or closes Gosys according to outcome without Kafka events`(
            outcome: Utfall,
            expectedGosysUpdate: Boolean,
        ) {
            every { behandling.utfall } returns outcome
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveUpdate } returns mockk<GosysOppgaveUpdate>()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verifyNoNewTreatments()
            verify(exactly = if (expectedGosysUpdate) 1 else 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = behandling,
                    systemContext = true,
                    throwExceptionIfFerdigstilt = true,
                )
            }
            verify(exactly = if (expectedGosysUpdate) 0 else 1) {
                gosysOppgaveService.avsluttGosysOppgave(behandling = behandling, throwExceptionIfFerdigstilt = false)
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @ParameterizedTest(name = "{0}: Gosys task is ignored")
        @MethodSource("utfallCases")
        fun `does not update ignored Gosys tasks but still closes completed tasks`(
            outcome: Utfall,
            expectedGosysUpdate: Boolean,
        ) {
            every { behandling.utfall } returns outcome
            every { behandling.gosysOppgaveRequired } returns true
            every { behandling.gosysOppgaveId } returns 123L
            every { behandling.gosysOppgaveUpdate } returns mockk<GosysOppgaveUpdate>()
            every { behandling.ignoreGosysOppgave } returns true

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verifyNoNewTreatments()
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = if (expectedGosysUpdate) 0 else 1) {
                gosysOppgaveService.avsluttGosysOppgave(behandling = behandling, throwExceptionIfFerdigstilt = false)
            }
            verify(exactly = 0) {
                gosysOppgaveService.addKommentar(
                    behandling = any(),
                    kommentar = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @ParameterizedTest(name = "Gosys required = {0}")
        @ValueSource(booleans = [false, true])
        fun `taken over by KA creates new gjenopptak treatment`(gosysOppgaveRequired: Boolean) {
            every { behandling.utfall } returns Utfall.GJENOPPTATT_STADFESTET
            every { behandling.nyGjenopptaksbehandlingKA } returns now
            every { behandling.gosysOppgaveRequired } returns gosysOppgaveRequired

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingEtter2027FromGjenopptakITrygderettenbehandlingEtter2027(behandling)
            }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(any())
            }
            verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
            verify(exactly = if (gosysOppgaveRequired) 1 else 0) {
                gosysOppgaveService.addKommentar(
                    behandling = behandling,
                    kommentar = "Klageinstansen har opprettet ny behandling i Kabal.",
                    systemContext = true,
                    throwExceptionIfFerdigstilt = false,
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @ParameterizedTest(name = "Gosys required = {0}")
        @ValueSource(booleans = [false, true])
        fun `opphevet flag creates treatment after Trygderetten`(gosysOppgaveRequired: Boolean) {
            every { behandling.utfall } returns Utfall.GJENOPPTATT_OPPHEVET
            every { behandling.nyBehandlingEtterTROpphevet } returns now
            every { behandling.gosysOppgaveRequired } returns gosysOppgaveRequired

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(behandling) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingEtter2027FromGjenopptakITrygderettenbehandlingEtter2027(any())
            }
            verify(exactly = if (gosysOppgaveRequired) 1 else 0) {
                gosysOppgaveService.addKommentar(
                    behandling = behandling,
                    kommentar = "Klageinstansen har opprettet ny behandling i Kabal etter at Trygderetten opphevet saken.",
                    systemContext = true,
                    throwExceptionIfFerdigstilt = false,
                )
            }
            verify(exactly = 0) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { gosysOppgaveService.avsluttGosysOppgave(behandling = any(), throwExceptionIfFerdigstilt = any()) }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        private fun verifyNoNewTreatments() {
            verify(exactly = 0) {
                gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingEtter2027(any())
            }
            verify(exactly = 0) {
                gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandlingFoer2027(any())
            }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingEtter2027FromGjenopptakITrygderettenbehandlingEtter2027(any())
            }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFoer2027FromGjenopptakITrygderettenbehandlingFoer2027(any())
            }
            verify(exactly = 0) { behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(any()) }
        }
    }

    private fun verifyGjenopptakKafkaEvent(expectedKafkaEvent: Boolean) {
        if (expectedKafkaEvent) {
            val savedKafkaEvent = slot<KafkaEvent>()
            verify(exactly = 1) { kafkaEventRepository.save(capture(savedKafkaEvent)) }
            val event = jacksonObjectMapper().readValue(savedKafkaEvent.captured.jsonPayload, BehandlingEvent::class.java)
            assertEquals(BehandlingEventType.GJENOPPTAKSBEHANDLING_AVSLUTTET, event.type)
            assertEquals(behandlingId.toString(), event.kabalReferanse)
        } else {
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }
    }

    @Nested
    inner class PesysBasedOnJournalpostTest {
        private val pesysGosysOppgaveUpdate =
            GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = "",
            )

        private fun <T : Behandling> T.setupPesys(gosysOppgaveRequired: Boolean): T {
            val behandling = this
            every { behandling.shouldNotifyVedtaksinstans() } answers { callOriginal() }
            every { behandling.id } returns behandlingId
            every { behandling.ferdigstilling } returns
                Ferdigstilling(
                    avsluttet = null,
                    avsluttetAvSaksbehandler = now,
                    navIdent = "",
                    navn = "",
                )
            every { behandling.tildeling } returns mockk { every { saksbehandlerident } returns "ident" }
            every { behandling.kildeReferanse } returns "kildereferanse"
            every { behandling.fagsystem } returns Fagsystem.PP01
            every { behandling.utfall } returns Utfall.STADFESTELSE
            every { behandling.gosysOppgaveRequired } returns gosysOppgaveRequired
            every { behandling.gosysOppgaveId } returns if (gosysOppgaveRequired) 123L else null
            every { behandling.gosysOppgaveUpdate } returns if (gosysOppgaveRequired) pesysGosysOppgaveUpdate else null
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
            return behandling
        }

        private fun verifyHandledLikeArena() {
            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = any(),
                    systemContext = any(),
                    throwExceptionIfFerdigstilt = any(),
                )
            }
            verify(exactly = 0) { klankeService.setToFinishedWithAppAccess(sakId = any(), input = any()) }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `Klagebehandling from Pesys based on journalpost updates GosysOppgave without Kafka event`() {
            mockk<Klagebehandling>(relaxed = true).setupPesys(gosysOppgaveRequired = true)

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verifyHandledLikeArena()
        }

        @Test
        fun `Klagebehandling from Pesys without GosysOppgave creates Kafka event`() {
            mockk<Klagebehandling>(relaxed = true).setupPesys(gosysOppgaveRequired = false)

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkebehandlingFoer2027 from Pesys based on journalpost not going to Trygderetten updates GosysOppgave without Kafka event`() {
            val behandling = mockk<AnkebehandlingFoer2027>(relaxed = true).setupPesys(gosysOppgaveRequired = true)
            every { behandling.shouldBeSentToTrygderetten() } returns false

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verifyHandledLikeArena()
        }

        @Test
        fun `AnkebehandlingEtter2027 from Pesys based on journalpost creates AnkeITrygderettenbehandlingEtter2027 and updates GosysOppgave`() {
            mockk<AnkebehandlingEtter2027>(relaxed = true).setupPesys(gosysOppgaveRequired = true)

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingEtter2027(any()) }
            verifyHandledLikeArena()
        }

        @Test
        fun `AnkeITrygderettenbehandlingEtter2027 from Pesys based on journalpost updates GosysOppgave without Kafka event`() {
            val behandling = mockk<AnkeITrygderettenbehandlingEtter2027>(relaxed = true).setupPesys(gosysOppgaveRequired = true)
            every { behandling.nyAnkebehandlingKA } returns null
            every { behandling.shouldCreateNewAnkebehandling() } answers { callOriginal() }
            every { behandling.shouldCreateNewBehandlingEtterTROpphevet() } answers { callOriginal() }
            every { behandling.shouldNotCreateNewBehandling() } answers { callOriginal() }

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingEtter2027FromAnkeITrygderettenbehandlingEtter2027(any()) }
            verifyHandledLikeArena()
        }

        @Test
        fun `BehandlingEtterTrygderettenOpphevet from Pesys based on journalpost updates GosysOppgave without Kafka event`() {
            mockk<BehandlingEtterTrygderettenOpphevet>(relaxed = true).setupPesys(gosysOppgaveRequired = true)

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verifyHandledLikeArena()
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class KafkaEventPerUtfall {
        private val expectedKafkaEventsByType =
            mapOf(
                Type.KLAGE to
                    mapOf(
                        Utfall.TRUKKET to true,
                        Utfall.RETUR to true,
                        Utfall.OPPHEVET to true,
                        Utfall.MEDHOLD to true,
                        Utfall.DELVIS_MEDHOLD to true,
                        Utfall.STADFESTELSE to true,
                        Utfall.AVVIST to true,
                        Utfall.UGUNST to true,
                        Utfall.HENLAGT to true,
                    ),
                Type.ANKE_FOER_2027 to
                    mapOf(
                        Utfall.TRUKKET to false,
                        Utfall.OPPHEVET to true,
                        Utfall.MEDHOLD to true,
                        Utfall.DELVIS_MEDHOLD to true,
                        Utfall.INNSTILLING_STADFESTELSE to false,
                        Utfall.INNSTILLING_AVVIST to false,
                        Utfall.UGUNST to true,
                        Utfall.HENLAGT to false,
                    ),
                Type.ANKE_I_TRYGDERETTEN_FOER_2027 to
                    mapOf(
                        Utfall.OPPHEVET to true,
                        Utfall.MEDHOLD to true,
                        Utfall.DELVIS_MEDHOLD to true,
                        Utfall.STADFESTELSE to false,
                        Utfall.AVVIST to false,
                        Utfall.HEVET to false,
                        Utfall.HENVIST to false,
                    ),
                Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET to
                    mapOf(
                        Utfall.TRUKKET to false,
                        Utfall.RETUR to true,
                        Utfall.OPPHEVET to true,
                        Utfall.MEDHOLD to true,
                        Utfall.DELVIS_MEDHOLD to true,
                        Utfall.STADFESTELSE to false,
                        Utfall.AVVIST to false,
                        Utfall.UGUNST to true,
                        Utfall.HENLAGT to false,
                    ),
                Type.OMGJOERINGSKRAV to
                    mapOf(
                        Utfall.MEDHOLD_ETTER_FVL_35 to true,
                        Utfall.BESLUTNING_IKKE_OMGJOERE to false,
                        Utfall.STADFESTET_ANNEN_BEGRUNNELSE to false,
                        Utfall.TRUKKET to false,
                        Utfall.UGUNST to true,
                        Utfall.HENLAGT to false,
                    ),
                Type.BEGJAERING_OM_GJENOPPTAK_FOER_2027 to
                    mapOf(
                        Utfall.MEDHOLD_ETTER_FVL_35 to true,
                        Utfall.INNSTILLING_GJENOPPTAS_KAS_VEDTAK_STADFESTES to false,
                        Utfall.INNSTILLING_GJENOPPTAS_IKKE to false,
                        Utfall.INNSTILLING_AVVIST to false,
                        Utfall.TRUKKET to false,
                        Utfall.UGUNST to true,
                        Utfall.HENLAGT to false,
                    ),
                Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027 to
                    mapOf(
                        Utfall.GJENOPPTATT_DELVIS_ELLER_FULLT_MEDHOLD to true,
                        Utfall.GJENOPPTATT_OPPHEVET to true,
                        Utfall.GJENOPPTATT_STADFESTET to false,
                        Utfall.IKKE_GJENOPPTATT to false,
                        Utfall.AVVIST to false,
                        Utfall.HEVET to false,
                    ),
                Type.ANKE_ETTER_2027 to
                    mapOf(
                        Utfall.OPPHEVET to true,
                        Utfall.MEDHOLD to true,
                        Utfall.DELVIS_MEDHOLD to true,
                        Utfall.INNSTILLING_STADFESTELSE to false,
                        Utfall.INNSTILLING_AVVIST to false,
                        Utfall.UGUNST to true,
                    ),
                Type.ANKE_I_TRYGDERETTEN_ETTER_2027 to
                    mapOf(
                        Utfall.OPPHEVET to true,
                        Utfall.MEDHOLD to true,
                        Utfall.DELVIS_MEDHOLD to true,
                        Utfall.STADFESTELSE to false,
                        Utfall.AVVIST to false,
                        Utfall.HEVET to false,
                        Utfall.HENVIST to false,
                    ),
                Type.BEGJAERING_OM_GJENOPPTAK_ETTER_2027 to
                    mapOf(
                        Utfall.MEDHOLD_ETTER_FVL_35 to true,
                        Utfall.INNSTILLING_GJENOPPTAS_KAS_VEDTAK_STADFESTES to false,
                        Utfall.INNSTILLING_GJENOPPTAS_IKKE to false,
                        Utfall.INNSTILLING_AVVIST to false,
                        Utfall.UGUNST to true,
                    ),
                Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_ETTER_2027 to
                    mapOf(
                        Utfall.GJENOPPTATT_DELVIS_ELLER_FULLT_MEDHOLD to true,
                        Utfall.GJENOPPTATT_OPPHEVET to true,
                        Utfall.GJENOPPTATT_STADFESTET to false,
                        Utfall.IKKE_GJENOPPTATT to false,
                        Utfall.AVVIST to false,
                        Utfall.HEVET to false,
                    ),
            )

        fun kafkaEventCases(): List<Arguments> =
            expectedKafkaEventsByType.flatMap { (type, outcomes) ->
                outcomes.map { (outcome, expectedKafkaEvent) ->
                    Arguments.of(type, outcome, expectedKafkaEvent)
                }
            }

        @Test
        fun `hard-coded outcome table exactly matches typeToUtfall`() {
            assertEquals(typeToUtfall.keys, expectedKafkaEventsByType.keys)
            typeToUtfall.forEach { (type, outcomes) ->
                assertEquals(outcomes, expectedKafkaEventsByType[type]?.keys, type.name)
            }
        }

        @ParameterizedTest(name = "{0} / {1}: Kafka event expected = {2}")
        @MethodSource("kafkaEventCases")
        fun `only listed treatment outcomes create Kafka events`(
            type: Type,
            outcome: Utfall,
            expectedKafkaEvent: Boolean,
        ) {
            val behandling = createBehandling(type = type, utfall = outcome)
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = if (expectedKafkaEvent) 1 else 0) { kafkaEventRepository.save(any()) }
        }

        @Test
        fun `AnkebehandlingFoer2027 with DELVIS_MEDHOLD creates Trygderetten treatment and Kafka event`() {
            val behandling = createBehandling(type = Type.ANKE_FOER_2027, utfall = Utfall.DELVIS_MEDHOLD)
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
            val savedKafkaEvent = slot<KafkaEvent>()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) { kafkaEventRepository.save(capture(savedKafkaEvent)) }
            val event = jacksonObjectMapper().readValue(savedKafkaEvent.captured.jsonPayload, BehandlingEvent::class.java)
            assertEquals(BehandlingEventType.ANKEBEHANDLING_AVSLUTTET, event.type)
        }

        @Test
        fun `AnkebehandlingEtter2027 with DELVIS_MEDHOLD creates Trygderetten treatment and Kafka event`() {
            val behandling = createBehandling(type = Type.ANKE_ETTER_2027, utfall = Utfall.DELVIS_MEDHOLD)
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling
            val savedKafkaEvent = slot<KafkaEvent>()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingEtter2027(any()) }
            verify(exactly = 1) { kafkaEventRepository.save(capture(savedKafkaEvent)) }
            val event = jacksonObjectMapper().readValue(savedKafkaEvent.captured.jsonPayload, BehandlingEvent::class.java)
            assertEquals(BehandlingEventType.ANKEBEHANDLING_AVSLUTTET, event.type)
        }

        @Test
        fun `Gjenopptaksbehandling with UGUNST and Gosys task updates Gosys without Kafka event`() {
            val behandling =
                createBehandling(type = Type.BEGJAERING_OM_GJENOPPTAK_FOER_2027, utfall = Utfall.UGUNST, gosysOppgaveRequired = true)
            every { behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(any()) } returns behandling

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 1) {
                gosysOppgaveService.updateGosysOppgaveOnCompletedBehandling(
                    behandling = behandling,
                    systemContext = true,
                    throwExceptionIfFerdigstilt = true,
                )
            }
            verify(exactly = 0) { kafkaEventRepository.save(any()) }
        }

        private fun createBehandling(
            type: Type,
            utfall: Utfall,
            gosysOppgaveRequired: Boolean = false,
        ): Behandling {
            val behandling =
                when (type) {
                    Type.KLAGE -> {
                        mockk<Klagebehandling>(relaxed = true)
                    }

                    Type.ANKE_FOER_2027 -> {
                        mockk<AnkebehandlingFoer2027>(relaxed = true)
                    }

                    Type.ANKE_ETTER_2027 -> {
                        mockk<AnkebehandlingEtter2027>(relaxed = true)
                    }

                    Type.ANKE_I_TRYGDERETTEN_FOER_2027 -> {
                        mockk<AnkeITrygderettenbehandlingFoer2027>(relaxed = true)
                    }

                    Type.ANKE_I_TRYGDERETTEN_ETTER_2027 -> {
                        mockk<AnkeITrygderettenbehandlingEtter2027>(relaxed = true)
                    }

                    Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET -> {
                        mockk<BehandlingEtterTrygderettenOpphevet>(relaxed = true)
                    }

                    Type.OMGJOERINGSKRAV -> {
                        mockk<Omgjoeringskravbehandling>(relaxed = true)
                    }

                    Type.BEGJAERING_OM_GJENOPPTAK_FOER_2027 -> {
                        mockk<GjenopptaksbehandlingFoer2027>(relaxed = true)
                    }

                    Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027 -> {
                        mockk<GjenopptakITrygderettenbehandlingFoer2027>(
                            relaxed = true,
                        )
                    }

                    Type.BEGJAERING_OM_GJENOPPTAK_ETTER_2027 -> {
                        mockk<GjenopptaksbehandlingEtter2027>(relaxed = true)
                    }

                    Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_ETTER_2027 -> {
                        mockk<GjenopptakITrygderettenbehandlingEtter2027>(relaxed = true)
                    }
                }

            every { behandling.id } returns behandlingId
            every { behandling.type } returns type
            every { behandling.utfall } returns utfall
            every { behandling.fagsystem } returns Fagsystem.FS36
            every { behandling.kildeReferanse } returns "kildereferanse"
            every { behandling.shouldNotifyVedtaksinstans() } answers { callOriginal() }
            every { behandling.gosysOppgaveRequired } returns gosysOppgaveRequired
            every { behandling.ignoreGosysOppgave } returns false
            every { behandling.gosysOppgaveId } returns if (gosysOppgaveRequired) 123L else null
            every { behandling.gosysOppgaveUpdate } returns if (gosysOppgaveRequired) mockk<GosysOppgaveUpdate>() else null
            every { behandling.ferdigstilling } returns
                Ferdigstilling(
                    avsluttet = null,
                    avsluttetAvSaksbehandler = now,
                    navIdent = "",
                    navn = "",
                )
            every { behandling.tildeling } returns mockk { every { saksbehandlerident } returns "ident" }

            when (behandling) {
                is AnkebehandlingFoer2027 -> {
                    every { behandling.shouldBeSentToTrygderetten() } answers { callOriginal() }
                    every { behandling.createAnkeITrygderettenbehandlingFoer2027Input() } returns mockk()
                }

                is AnkebehandlingEtter2027 -> {
                    every { behandling.shouldBeSentToTrygderetten() } answers { callOriginal() }
                    every { behandling.createAnkeITrygderettenbehandlingEtter2027Input() } returns mockk()
                }

                is AnkeITrygderettenbehandlingFoer2027 -> {
                    every { behandling.nyAnkebehandlingKA } returns null
                    every { behandling.nyBehandlingEtterTROpphevet } returns null
                    every { behandling.shouldCreateNewAnkebehandling() } answers { callOriginal() }
                    every { behandling.shouldCreateNewBehandlingEtterTROpphevet() } answers { callOriginal() }
                    every { behandling.shouldNotCreateNewBehandling() } answers { callOriginal() }
                }

                is AnkeITrygderettenbehandlingEtter2027 -> {
                    every { behandling.nyAnkebehandlingKA } returns null
                    every { behandling.nyBehandlingEtterTROpphevet } returns null
                    every { behandling.shouldCreateNewAnkebehandling() } answers { callOriginal() }
                    every { behandling.shouldCreateNewBehandlingEtterTROpphevet() } answers { callOriginal() }
                    every { behandling.shouldNotCreateNewBehandling() } answers { callOriginal() }
                }

                is Omgjoeringskravbehandling -> {
                    every { behandling.shouldBeSentToVedtaksinstans() } answers { callOriginal() }
                    every { behandling.shouldBeCompletedInKA() } answers { callOriginal() }
                }

                is GjenopptaksbehandlingFoer2027 -> {
                    every { behandling.shouldBeSentToTrygderetten() } answers { callOriginal() }
                    every { behandling.shouldBeSentToVedtaksinstans() } answers { callOriginal() }
                    every { behandling.shouldBeCompletedInKA() } answers { callOriginal() }
                }

                is GjenopptakITrygderettenbehandlingFoer2027 -> {
                    every { behandling.nyGjenopptaksbehandlingKA } returns null
                    every { behandling.nyBehandlingEtterTROpphevet } returns null
                    every { behandling.shouldCreateNewGjenopptaksbehandling() } answers { callOriginal() }
                    every { behandling.shouldCreateNewBehandlingEtterTROpphevet() } answers { callOriginal() }
                    every { behandling.shouldBeCompletedInKA() } answers { callOriginal() }
                }

                is GjenopptaksbehandlingEtter2027 -> {
                    every { behandling.shouldBeSentToTrygderetten() } answers { callOriginal() }
                    every { behandling.shouldBeSentToVedtaksinstans() } answers { callOriginal() }
                    every { behandling.shouldBeCompletedInKA() } answers { callOriginal() }
                    every { behandling.createGjenopptakITrygderettenbehandlingEtter2027Input() } returns mockk()
                }

                is GjenopptakITrygderettenbehandlingEtter2027 -> {
                    every { behandling.nyGjenopptaksbehandlingKA } returns null
                    every { behandling.nyBehandlingEtterTROpphevet } returns null
                    every { behandling.shouldCreateNewGjenopptaksbehandling() } answers { callOriginal() }
                    every { behandling.shouldCreateNewBehandlingEtterTROpphevet() } answers { callOriginal() }
                    every { behandling.shouldBeCompletedInKA() } answers { callOriginal() }
                }
            }

            return behandling
        }
    }
}
