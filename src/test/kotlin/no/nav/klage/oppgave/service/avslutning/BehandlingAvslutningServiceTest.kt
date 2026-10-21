package no.nav.klage.oppgave.service.avslutning

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
import no.nav.klage.oppgave.clients.klagefssproxy.domain.GetSakAppAccessInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFinishedInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFromKlanke
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingEtterTrygderettenOpphevet
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandling
import no.nav.klage.oppgave.domain.behandling.Gjenopptaksbehandling
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingBasedOnJournalpost
import no.nav.klage.oppgave.domain.behandling.GjenopptaksbehandlingBasedOnKabalBehandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.Omgjoeringskravbehandling
import no.nav.klage.oppgave.domain.behandling.OmgjoeringskravbehandlingBasedOnJournalpost
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `OmgjoeringskravbehandlingBasedOnJournalpost should not update infotrygd, only Gosys`() {
            val behandling =
                mockk<OmgjoeringskravbehandlingBasedOnJournalpost>(relaxed = true) {
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
    inner class GjenopptaksbehandlingTest {
        val behandling =
            mockk<Gjenopptaksbehandling>(relaxed = true) {
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
        fun `GjenopptaksbehandlingBasedOnJournalpost with utfall MEDHOLD_ETTER_FVL_35 should update Gosys`() {
            val behandling =
                mockk<GjenopptaksbehandlingBasedOnJournalpost>(relaxed = true) {
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `GjenopptaksbehandlingBasedOnKabalBehandling with utfall MEDHOLD_ETTER_FVL_35 should create Kafka message`() {
            val behandling =
                mockk<GjenopptaksbehandlingBasedOnKabalBehandling>(relaxed = true) {
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `Gjenopptaksbehandling with Gosysoppgave with utfall going to Trygderetten creates GjenopptakITrygderettenbehandling and updates GosysOppgave`() {
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
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 1) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `Gjenopptaksbehandling without Gosysoppgave with utfall going to Trygderetten creates GjenopptakITrygderettenbehandling`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.INNSTILLING_GJENOPPTAS_KAS_VEDTAK_STADFESTES
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 1) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `Gjenopptaksbehandling with gosysoppgave and utfall not going to Trygderetten or vedtaksinstans updates GosysOppgave`() {
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
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `Gjenopptaksbehandling without gosysoppgave and utfall not going to Trygderetten does nothing external`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.TRUKKET
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
    inner class GjenopptakITrygderettenbehandlingTest {
        val behandling =
            mockk<GjenopptakITrygderettenbehandling>(relaxed = true) {
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
        fun `GjenopptakITrygderettenbehandling without gosysoppgave and utfall not going vedtaksinstans does nothing external`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.IKKE_GJENOPPTATT
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `GjenopptakITrygderettenbehandling with gosysoppgave and utfall not going vedtaksinstans finalizes GosysOppgave`() {
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
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `GjenopptakITrygderettenbehandling without gosysoppgave and utfall opphevet new behandling KA creates new behandling`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.GJENOPPTATT_OPPHEVET
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()
            every { behandling.nyBehandlingEtterTROpphevet } returns now

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `GjenopptakITrygderettenbehandling with gosysoppgave and utfall opphevet and new behandling KA creates new behandling and updates GosysOppgave`() {
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
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 1) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `GjenopptakITrygderettenbehandling without gosysoppgave and utfall opphevet new behandling VL sends Kafka message`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.GJENOPPTATT_OPPHEVET
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()
            every { behandling.nyBehandlingEtterTROpphevet } returns null

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `GjenopptakITrygderettenbehandling with gosysoppgave and utfall opphevet new behandling VL updates GosysOppgave`() {
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
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `GjenopptakITrygderettenbehandling without gosysoppgave and utfall gjenopptatt - delvis eller fullt medhold sends Kafka message`() {
            every { behandling.fagsystem } returns Fagsystem.IT01
            every { behandling.gosysOppgaveRequired } returns false
            every { behandling.utfall } returns Utfall.GJENOPPTATT_DELVIS_ELLER_FULLT_MEDHOLD
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `GjenopptakITrygderettenbehandling with gosysoppgave and utfall gjenopptatt - delvis eller fullt medhold updates GosysOppgave`() {
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
            every { behandling.createGjenopptakITrygderettenbehandlingInput() } returns mockk()

            behandlingAvslutningService.avsluttBehandling(behandlingId)

            verify(exactly = 0) { ankebehandlingService.createAnkebehandlingFoer2027FromAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) { ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFoer2027(any()) }
            verify(exactly = 0) {
                behandlingEtterTrygderettenOpphevetService.createBehandlingEtterTrygderettenOpphevet(
                    any(),
                )
            }
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 0) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `GjenopptakITrygderettenbehandling tagged with nyGjenopptaksbehandlingKA without gosysOppgave creates new Gjenopptaksbehandling`() {
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 1) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
        fun `GjenopptakITrygderettenbehandling tagged with nyAnkebehandlingKA and with gosysOppgave creates new AnkebehandlingFoer2027 and notifies GosysOppgave`() {
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
            verify(exactly = 0) { gjenopptakITrygderettenbehandlingService.createGjenopptakITrygderettenbehandling(any()) }
            verify(exactly = 1) {
                gjenopptaksbehandlingService.createGjenopptaksbehandlingFromGjenopptakITrygderettenbehandling(
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
    inner class PesysBasedOnJournalpostTest {
        private val pesysGosysOppgaveUpdate =
            GosysOppgaveUpdate(
                oppgaveUpdateTildeltEnhetsnummer = "123",
                oppgaveUpdateMappeId = null,
                oppgaveUpdateKommentar = "",
            )

        private fun <T : Behandling> T.setupPesys(gosysOppgaveRequired: Boolean): T {
            val behandling = this
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
                Type.BEGJAERING_OM_GJENOPPTAK to
                    mapOf(
                        Utfall.MEDHOLD_ETTER_FVL_35 to true,
                        Utfall.INNSTILLING_GJENOPPTAS_KAS_VEDTAK_STADFESTES to false,
                        Utfall.INNSTILLING_GJENOPPTAS_IKKE to false,
                        Utfall.INNSTILLING_AVVIST to false,
                        Utfall.TRUKKET to false,
                        Utfall.UGUNST to true,
                        Utfall.HENLAGT to false,
                    ),
                Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN to
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
                        Utfall.TRUKKET to false,
                        Utfall.OPPHEVET to true,
                        Utfall.MEDHOLD to true,
                        Utfall.DELVIS_MEDHOLD to true,
                        Utfall.INNSTILLING_STADFESTELSE to false,
                        Utfall.INNSTILLING_AVVIST to false,
                        Utfall.UGUNST to true,
                        Utfall.HENLAGT to false,
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
                assertEquals(outcomes, expectedKafkaEventsByType[type]?.keys)
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
            val behandling = createBehandling(type = Type.BEGJAERING_OM_GJENOPPTAK, utfall = Utfall.UGUNST, gosysOppgaveRequired = true)
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
                    Type.KLAGE -> mockk<Klagebehandling>(relaxed = true)
                    Type.ANKE_FOER_2027 -> mockk<AnkebehandlingFoer2027>(relaxed = true)
                    Type.ANKE_ETTER_2027 -> mockk<AnkebehandlingEtter2027>(relaxed = true)
                    Type.ANKE_I_TRYGDERETTEN_FOER_2027 -> mockk<AnkeITrygderettenbehandlingFoer2027>(relaxed = true)
                    Type.ANKE_I_TRYGDERETTEN_ETTER_2027 -> mockk<AnkeITrygderettenbehandlingEtter2027>(relaxed = true)
                    Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET -> mockk<BehandlingEtterTrygderettenOpphevet>(relaxed = true)
                    Type.OMGJOERINGSKRAV -> mockk<Omgjoeringskravbehandling>(relaxed = true)
                    Type.BEGJAERING_OM_GJENOPPTAK -> mockk<Gjenopptaksbehandling>(relaxed = true)
                    Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN -> mockk<GjenopptakITrygderettenbehandling>(relaxed = true)
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

                is Gjenopptaksbehandling -> {
                    every { behandling.shouldBeSentToTrygderetten() } answers { callOriginal() }
                    every { behandling.shouldBeSentToVedtaksinstans() } answers { callOriginal() }
                    every { behandling.shouldBeCompletedInKA() } answers { callOriginal() }
                }

                is GjenopptakITrygderettenbehandling -> {
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
