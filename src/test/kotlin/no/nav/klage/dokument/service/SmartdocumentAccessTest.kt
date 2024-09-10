package no.nav.klage.dokument.service

import io.micrometer.core.instrument.MeterRegistry
import io.mockk.every
import io.mockk.mockk
import no.nav.klage.dokument.api.view.DocumentAccessView
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.kodeverk.FlowState
import no.nav.klage.oppgave.config.getHistogram
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.BehandlingRole
import no.nav.klage.oppgave.domain.klage.Ferdigstilling
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class SmartdocumentAccessTest {

    private lateinit var dokumentUnderArbeidService: DokumentUnderArbeidService

    private val dokumentUnderArbeidRepository = mockk<DokumentUnderArbeidRepository>()

    private val behandlingService = mockk<BehandlingService>()

    private val innloggetSaksbehandlerService = mockk<InnloggetSaksbehandlerService>()

    @BeforeEach
    fun setUp() {
        val meterRegistry = mockk<MeterRegistry>(relaxed = true)
        every { meterRegistry.getHistogram(name = "", baseUnit = "") } returns mockk()

        dokumentUnderArbeidService = DokumentUnderArbeidService(
            dokumentUnderArbeidRepository = dokumentUnderArbeidRepository,
            dokumentUnderArbeidCommonService = mockk(),
            opplastetDokumentUnderArbeidAsHoveddokumentRepository = mockk(),
            opplastetDokumentUnderArbeidAsVedleggRepository = mockk(),
            smartDokumentUnderArbeidAsHoveddokumentRepository = mockk(),
            smartDokumentUnderArbeidAsVedleggRepository = mockk(),
            journalfoertDokumentUnderArbeidRepository = mockk(),
            mellomlagerService = mockk(),
            smartEditorApiGateway = mockk(),
            behandlingService = behandlingService,
            kabalDocumentGateway = mockk(),
            applicationEventPublisher = mockk(),
            innloggetSaksbehandlerService = innloggetSaksbehandlerService,
            dokumentService = mockk(),
            eregClient = mockk(),
            innholdsfortegnelseService = mockk(),
            safFacade = mockk(),
            dokumentMapper = mockk(),
            systembrukerIdent = "SYSTEMBRUKER",
            kafkaInternalEventService = mockk(),
            saksbehandlerService = mockk(),
            meterRegistry = meterRegistry,
            partSearchService = mockk(),
            kodeverkService = mockk(),
            dokDistKanalService = mockk(),
            kabalJsonToPdfService = mockk(),
            tokenUtil = mockk()
        )

        every { innloggetSaksbehandlerService.getInnloggetIdent() } returns "navident"
    }

    @Test
    fun `saksbehandler does not have WRITE access if behandling is finished`() {
        val behandling = mockk<Behandling>()
        val dua = mockk<DokumentUnderArbeid>(relaxed = true)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns false
        every { behandlingService.getBehandlingAndCheckLeseTilgangForPerson(any()) } returns behandling
        every { behandling.getRoleInBehandling(any()) } returns BehandlingRole.KABAL_SAKSBEHANDLING
        every { behandling.ferdigstilling } returns Ferdigstilling(
            avsluttet = LocalDateTime.now(),
            avsluttetAvSaksbehandler = LocalDateTime.now(),
            navIdent = "navident",
            navn = "Saks Behandler",
        )
        every { behandling.medunderskriverFlowState } returns FlowState.NOT_SENT
        every { dokumentUnderArbeidRepository.findById(any()).get() } returns dua
        every { dua.creatorRole } returns BehandlingRole.KABAL_SAKSBEHANDLING

        val documentAccessView = dokumentUnderArbeidService.getSmartdocumentAccess(
            behandlingId = UUID.randomUUID(),
            dokumentId = UUID.randomUUID(),
        )

        assertThat(documentAccessView.access).isEqualTo(DocumentAccessView.Access.READ)
    }

    @Test
    fun `saksbehandler has write access because NOT_SENT`() {
        val behandling = mockk<Behandling>()
        val dua = mockk<DokumentUnderArbeid>(relaxed = true)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns false
        every { behandlingService.getBehandlingAndCheckLeseTilgangForPerson(any()) } returns behandling
        every { behandling.getRoleInBehandling(any()) } returns BehandlingRole.KABAL_SAKSBEHANDLING
        every { behandling.ferdigstilling } returns null
        every { behandling.medunderskriverFlowState } returns FlowState.NOT_SENT
        every { dokumentUnderArbeidRepository.findById(any()).get() } returns dua
        every { dua.creatorRole } returns BehandlingRole.KABAL_SAKSBEHANDLING

        val documentAccessView = dokumentUnderArbeidService.getSmartdocumentAccess(
            behandlingId = UUID.randomUUID(),
            dokumentId = UUID.randomUUID(),
        )

        assertThat(documentAccessView.access).isEqualTo(DocumentAccessView.Access.WRITE)
    }

    @Test
    fun `saksbehandler has write access because RETURNED`() {
        val behandling = mockk<Behandling>()
        val dua = mockk<DokumentUnderArbeid>(relaxed = true)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns false
        every { behandlingService.getBehandlingAndCheckLeseTilgangForPerson(any()) } returns behandling
        every { behandling.getRoleInBehandling(any()) } returns BehandlingRole.KABAL_SAKSBEHANDLING
        every { behandling.ferdigstilling } returns null
        every { behandling.medunderskriverFlowState } returns FlowState.RETURNED
        every { dokumentUnderArbeidRepository.findById(any()).get() } returns dua
        every { dua.creatorRole } returns BehandlingRole.KABAL_SAKSBEHANDLING

        val documentAccessView = dokumentUnderArbeidService.getSmartdocumentAccess(
            behandlingId = UUID.randomUUID(),
            dokumentId = UUID.randomUUID(),
        )

        assertThat(documentAccessView.access).isEqualTo(DocumentAccessView.Access.WRITE)
    }

    @Test
    fun `saksbehandler has READ access because behandling is at MU`() {
        val behandling = mockk<Behandling>()
        val dua = mockk<DokumentUnderArbeid>(relaxed = true)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns false
        every { behandlingService.getBehandlingAndCheckLeseTilgangForPerson(any()) } returns behandling
        every { behandling.getRoleInBehandling(any()) } returns BehandlingRole.KABAL_SAKSBEHANDLING
        every { behandling.ferdigstilling } returns null
        every { behandling.medunderskriverFlowState } returns FlowState.SENT
        every { dokumentUnderArbeidRepository.findById(any()).get() } returns dua
        every { dua.creatorRole } returns BehandlingRole.KABAL_SAKSBEHANDLING

        val documentAccessView = dokumentUnderArbeidService.getSmartdocumentAccess(
            behandlingId = UUID.randomUUID(),
            dokumentId = UUID.randomUUID(),
        )

        assertThat(documentAccessView.access).isEqualTo(DocumentAccessView.Access.READ)
    }

    @Test
    fun `MU has WRITE access because behandling is at MU`() {
        val behandling = mockk<Behandling>()
        val dua = mockk<DokumentUnderArbeid>(relaxed = true)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns false
        every { behandlingService.getBehandlingAndCheckLeseTilgangForPerson(any()) } returns behandling
        every { behandling.getRoleInBehandling(any()) } returns BehandlingRole.KABAL_MEDUNDERSKRIVER
        every { behandling.ferdigstilling } returns null
        every { behandling.medunderskriverFlowState } returns FlowState.SENT
        every { dokumentUnderArbeidRepository.findById(any()).get() } returns dua
        every { dua.creatorRole } returns BehandlingRole.KABAL_SAKSBEHANDLING

        val documentAccessView = dokumentUnderArbeidService.getSmartdocumentAccess(
            behandlingId = UUID.randomUUID(),
            dokumentId = UUID.randomUUID(),
        )

        assertThat(documentAccessView.access).isEqualTo(DocumentAccessView.Access.WRITE)
    }

    @Test
    fun `MU has READ access because behandling is at saksbehandler`() {
        val behandling = mockk<Behandling>()
        val dua = mockk<DokumentUnderArbeid>(relaxed = true)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns false
        every { behandlingService.getBehandlingAndCheckLeseTilgangForPerson(any()) } returns behandling
        every { behandling.getRoleInBehandling(any()) } returns BehandlingRole.KABAL_MEDUNDERSKRIVER
        every { behandling.ferdigstilling } returns null
        every { behandling.medunderskriverFlowState } returns FlowState.RETURNED
        every { dokumentUnderArbeidRepository.findById(any()).get() } returns dua
        every { dua.creatorRole } returns BehandlingRole.KABAL_SAKSBEHANDLING

        val documentAccessView = dokumentUnderArbeidService.getSmartdocumentAccess(
            behandlingId = UUID.randomUUID(),
            dokumentId = UUID.randomUUID(),
        )

        assertThat(documentAccessView.access).isEqualTo(DocumentAccessView.Access.READ)
    }

    @Test
    fun `MU has READ access because behandling is at saksbehandler 2`() {
        val behandling = mockk<Behandling>()
        val dua = mockk<DokumentUnderArbeid>(relaxed = true)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns false
        every { behandlingService.getBehandlingAndCheckLeseTilgangForPerson(any()) } returns behandling
        every { behandling.getRoleInBehandling(any()) } returns BehandlingRole.KABAL_MEDUNDERSKRIVER
        every { behandling.ferdigstilling } returns null
        every { behandling.medunderskriverFlowState } returns FlowState.NOT_SENT
        every { dokumentUnderArbeidRepository.findById(any()).get() } returns dua
        every { dua.creatorRole } returns BehandlingRole.KABAL_SAKSBEHANDLING

        val documentAccessView = dokumentUnderArbeidService.getSmartdocumentAccess(
            behandlingId = UUID.randomUUID(),
            dokumentId = UUID.randomUUID(),
        )

        assertThat(documentAccessView.access).isEqualTo(DocumentAccessView.Access.READ)
    }

    @Test
    fun `ROL has WRITE access to own document because behandling is at ROL`() {
        val behandling = mockk<Behandling>()
        val dua = mockk<DokumentUnderArbeid>(relaxed = true)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns false
        every { behandlingService.getBehandlingAndCheckLeseTilgangForPerson(any()) } returns behandling
        every { behandling.getRoleInBehandling(any()) } returns BehandlingRole.KABAL_ROL
        every { behandling.ferdigstilling } returns null
        every { behandling.rolFlowState } returns FlowState.SENT
        every { dokumentUnderArbeidRepository.findById(any()).get() } returns dua
        every { dua.creatorRole } returns BehandlingRole.KABAL_ROL

        val documentAccessView = dokumentUnderArbeidService.getSmartdocumentAccess(
            behandlingId = UUID.randomUUID(),
            dokumentId = UUID.randomUUID(),
        )

        assertThat(documentAccessView.access).isEqualTo(DocumentAccessView.Access.WRITE)
    }

    @Test
    fun `ROL has READ access to own document because behandling is RETURNED`() {
        val behandling = mockk<Behandling>()
        val dua = mockk<DokumentUnderArbeid>(relaxed = true)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns false
        every { behandlingService.getBehandlingAndCheckLeseTilgangForPerson(any()) } returns behandling
        every { behandling.getRoleInBehandling(any()) } returns BehandlingRole.KABAL_ROL
        every { behandling.ferdigstilling } returns null
        every { behandling.rolFlowState } returns FlowState.RETURNED
        every { dokumentUnderArbeidRepository.findById(any()).get() } returns dua
        every { dua.creatorRole } returns BehandlingRole.KABAL_ROL

        val documentAccessView = dokumentUnderArbeidService.getSmartdocumentAccess(
            behandlingId = UUID.randomUUID(),
            dokumentId = UUID.randomUUID(),
        )

        assertThat(documentAccessView.access).isEqualTo(DocumentAccessView.Access.READ)
    }

    @Test
    fun `ROL has READ access to saksbehandlers document even if SENT to ROL`() {
        val behandling = mockk<Behandling>()
        val dua = mockk<DokumentUnderArbeid>(relaxed = true)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns false
        every { behandlingService.getBehandlingAndCheckLeseTilgangForPerson(any()) } returns behandling
        every { behandling.getRoleInBehandling(any()) } returns BehandlingRole.KABAL_ROL
        every { behandling.ferdigstilling } returns null
        every { behandling.rolFlowState } returns FlowState.SENT
        every { dokumentUnderArbeidRepository.findById(any()).get() } returns dua
        every { dua.creatorRole } returns BehandlingRole.KABAL_SAKSBEHANDLING

        val documentAccessView = dokumentUnderArbeidService.getSmartdocumentAccess(
            behandlingId = UUID.randomUUID(),
            dokumentId = UUID.randomUUID(),
        )

        assertThat(documentAccessView.access).isEqualTo(DocumentAccessView.Access.READ)
    }

    @Test
    fun `saksbehandler has READ access to ROLs document even if RETURNED`() {
        val behandling = mockk<Behandling>()
        val dua = mockk<DokumentUnderArbeid>(relaxed = true)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns false
        every { behandlingService.getBehandlingAndCheckLeseTilgangForPerson(any()) } returns behandling
        every { behandling.getRoleInBehandling(any()) } returns BehandlingRole.KABAL_SAKSBEHANDLING
        every { behandling.ferdigstilling } returns null
        every { behandling.rolFlowState } returns FlowState.RETURNED
        every { dokumentUnderArbeidRepository.findById(any()).get() } returns dua
        every { dua.creatorRole } returns BehandlingRole.KABAL_ROL

        val documentAccessView = dokumentUnderArbeidService.getSmartdocumentAccess(
            behandlingId = UUID.randomUUID(),
            dokumentId = UUID.randomUUID(),
        )

        assertThat(documentAccessView.access).isEqualTo(DocumentAccessView.Access.READ)
    }

    @Test
    fun `merkantil has WRITE access to document created by the system`() {
        val behandling = mockk<Behandling>()
        val dua = mockk<DokumentUnderArbeid>(relaxed = true)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns true
        every { behandlingService.getBehandlingAndCheckLeseTilgangForPerson(any()) } returns behandling
        every { behandling.getRoleInBehandling(any()) } returns BehandlingRole.NONE
        every { behandling.ferdigstilling } returns null
        every { behandling.rolFlowState } returns FlowState.RETURNED
        every { dokumentUnderArbeidRepository.findById(any()).get() } returns dua
        every { dua.creatorRole } returns BehandlingRole.NONE

        val documentAccessView = dokumentUnderArbeidService.getSmartdocumentAccess(
            behandlingId = UUID.randomUUID(),
            dokumentId = UUID.randomUUID(),
        )

        assertThat(documentAccessView.access).isEqualTo(DocumentAccessView.Access.WRITE)
    }

}