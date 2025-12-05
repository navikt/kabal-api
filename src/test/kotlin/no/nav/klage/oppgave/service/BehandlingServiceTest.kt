package no.nav.klage.oppgave.service

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import io.mockk.mockk
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Language
import no.nav.klage.dokument.domain.dokumenterunderarbeid.SmartdokumentUnderArbeidAsHoveddokument
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.GosysOppgaveInput
import no.nav.klage.oppgave.api.view.GosysOppgaveUpdateInput
import no.nav.klage.oppgave.api.view.MedunderskriverFlowStateResponse
import no.nav.klage.oppgave.api.view.MedunderskriverWrapped
import no.nav.klage.oppgave.clients.arbeidoginntekt.ArbeidOgInntektClient
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.tilgangsmaskinen.TilgangsmaskinenRestClient
import no.nav.klage.oppgave.db.PostgresIntegrationTestBase
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingRole.KABAL_SAKSBEHANDLING
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Ferdigstilling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.exceptions.SectionedValidationErrorWithDetailsException
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.Collections.emptySortedSet

@ActiveProfiles("local")
@DataJpaTest
class BehandlingServiceTest : PostgresIntegrationTestBase() {

    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @SpykBean
    lateinit var tilgangService: TilgangService

    @MockkBean
    lateinit var innloggetSaksbehandlerService: InnloggetSaksbehandlerService

    @MockkBean
    lateinit var tilgangsmaskinenRestClient: TilgangsmaskinenRestClient

    @MockkBean(relaxed = true)
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockkBean
    lateinit var personService: PersonService

    @MockkBean
    lateinit var egenAnsattService: EgenAnsattService

    @MockkBean
    lateinit var dokumentUnderArbeidRepository: DokumentUnderArbeidRepository

    @MockkBean
    lateinit var kakaApiGateway: KakaApiGateway

    @MockkBean
    lateinit var dokumentService: DokumentService

    @MockkBean
    lateinit var kabalInnstillingerService: KabalInnstillingerService

    @MockkBean
    lateinit var saksbehandlerService: SaksbehandlerService

    @MockkBean
    lateinit var arbeidOgInntektClient: ArbeidOgInntektClient

    @MockkBean
    lateinit var fssProxyClient: KlageFssProxyClient

    @MockkBean
    lateinit var eregClient: EregClient

    @MockkBean
    lateinit var behandlingMapper: BehandlingMapper

    //Because of Hibernate Envers and our setup for audit logs.
    @MockkBean
    lateinit var tokenUtil: TokenUtil

    lateinit var behandlingService: BehandlingService

    private val SAKSBEHANDLER_IDENT = "SAKSBEHANDLER_IDENT"

    lateinit var behandlingId: UUID

    @BeforeEach
    fun setup() {
        val behandling = simpleInsert()
        behandlingId = behandling.id
        behandlingService = BehandlingService(
            behandlingRepository = behandlingRepository,
            tilgangService = tilgangService,
            applicationEventPublisher = applicationEventPublisher,
            kakaApiGateway = kakaApiGateway,
            dokumentService = dokumentService,
            dokumentUnderArbeidRepository = dokumentUnderArbeidRepository,
            kabalInnstillingerService = kabalInnstillingerService,
            innloggetSaksbehandlerService = innloggetSaksbehandlerService,
            arbeidOgInntektClient = arbeidOgInntektClient,
            fssProxyClient = fssProxyClient,
            eregClient = eregClient,
            saksbehandlerService = saksbehandlerService,
            behandlingMapper = behandlingMapper,
            historyService = mockk(),
            systembrukerIdent = "SYSTEMBRUKER",
            kafkaInternalEventService = mockk(),
            partSearchService = mockk(),
            safFacade = mockk(),
            tokenUtil = mockk(),
            gosysOppgaveService = mockk(),
            kodeverkService = mockk(),
            behandlingEndretKafkaProducer = mockk(),
            klageNotificationsApiClient = mockk(relaxed = true),
        )
        every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling) } returns Unit
        every { innloggetSaksbehandlerService.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
        every { tilgangService.verifyInnloggetSaksbehandlersTilgangTil(any()) } returns Unit
        every { tilgangService.hasSaksbehandlerAccessTo(any()) } returns TilgangService.Access(true, "")
        every { saksbehandlerService.hasKabalOppgavestyringAlleEnheterRole(any()) } returns false
        every { behandlingMapper.mapToMedunderskriverWrapped(any()) } returns MedunderskriverWrapped(
            employee = null,
            modified = LocalDateTime.now(),
            flowState = FlowState.SENT,
        )
        every { behandlingMapper.mapToMedunderskriverFlowStateResponse(any()) } returns MedunderskriverFlowStateResponse(
            employee = null,
            modified = LocalDateTime.now(),
            flowState = FlowState.SENT
        )
        every { kakaApiGateway.getValidationErrors(any()) } returns emptyList()
        every { dokumentUnderArbeidRepository.findByBehandlingIdAndMarkertFerdigIsNull(any()) } returns emptySortedSet()
    }

    @Nested
    inner class GetBehandlingForUpdate {
        @Test
        fun `getBehandlingForUpdate ok`() {
            val behandling = simpleInsert()

            assertThat(
                behandlingService.getBehandlingForUpdate(
                    behandlingId = behandling.id,
                    ignoreCheckSkrivetilgang = true
                )
            ).isEqualTo(behandling)
        }

        @Test
        fun `getBehandlingForUpdate sjekker skrivetilgang, fanger riktig exception`() {
            val behandling = simpleInsert()

            every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling) }.throws(
                BehandlingAvsluttetException("")
            )

            assertThrows<BehandlingAvsluttetException> { behandlingService.getBehandlingForUpdate(behandling.id) }
        }
    }

    @Nested
    inner class SetMedunderskriverIdent {
//        @Test
//        fun `setMedunderskriverIdent kan sette medunderskriver til null`() {
//            behandlingService.setMedunderskriverNavIdent(
//                behandlingId = behandlingId,
//                utfoerendeSaksbehandlerIdent = SAKSBEHANDLER_IDENT,
//                navIdent = null,
//            )
//
//            val output = behandlingRepository.getReferenceById(behandlingId)
//
//            assertThat(output.medunderskriver?.saksbehandlerident).isNull()
//            assertThat(output.medunderskriverHistorikk).hasSize(1)
//        }
    }

    //TODO fix
    @Nested
    inner class SwitchMedunderskriverFlowState {
//        @Test
//        fun `switchMedunderskriverFlowState gir forventet feil når bruker er saksbehandler og medunderskriver ikke er satt`() {
//            assertThrows<BehandlingManglerMedunderskriverException> {
//                behandlingService.switchMedunderskriverFlowState(
//                    behandlingId,
//                    SAKSBEHANDLER_IDENT
//                )
//            }
//        }

//        @Test
//        fun `switchMedunderskriverFlowState gir forventet status når bruker er saksbehandler og medunderskriver er satt`() {
//            behandlingService.setMedunderskriverFlowState(
//                behandlingId,
//                MEDUNDERSKRIVER_IDENT,
//                SAKSBEHANDLER_IDENT
//            )
//
//            behandlingService.switchMedunderskriverFlowState(
//                behandlingId,
//                SAKSBEHANDLER_IDENT
//            )
//
//            val output = behandlingRepository.getReferenceById(behandlingId)
//            assertThat(output.medunderskriverFlowState).isEqualTo(FlowState.SENT)
//        }

//        @Test
//        fun `switchMedunderskriverFlowState gir forventet status når bruker er medunderskriver`() {
//            every { innloggetSaksbehandlerService.getInnloggetIdent() } returns MEDUNDERSKRIVER_IDENT
//
//            behandlingService.setMedunderskriverFlowState(
//                behandlingId,
//                MEDUNDERSKRIVER_IDENT,
//                SAKSBEHANDLER_IDENT,
//                FlowState.SENT,
//            )
//
//            behandlingService.switchMedunderskriverFlowState(
//                behandlingId,
//                MEDUNDERSKRIVER_IDENT
//            )
//
//            val output = behandlingRepository.getReferenceById(behandlingId)
//
//            assertThat(output.medunderskriverFlowState).isEqualTo(FlowState.RETURNED)
//        }

//        @Test
//        fun `flere kall til switchMedunderskriverFlowState fra saksbehandler er idempotent`() {
//            behandlingService.setMedunderskriverFlowState(
//                behandlingId,
//                MEDUNDERSKRIVER_IDENT,
//                SAKSBEHANDLER_IDENT
//            )
//
//            behandlingService.switchMedunderskriverFlowState(
//                behandlingId,
//                SAKSBEHANDLER_IDENT
//            )
//
//            behandlingService.switchMedunderskriverFlowState(
//                behandlingId,
//                SAKSBEHANDLER_IDENT
//            )
//
//            val output = behandlingRepository.getReferenceById(behandlingId)
//
//            assertThat(output.medunderskriverFlowState).isEqualTo(FlowState.SENT)
//        }
//
//        @Test
//        fun `flere kall til switchMedunderskriverFlowState fra medunderskriver er idempotent`() {
//            every { innloggetSaksbehandlerService.getInnloggetIdent() } returns MEDUNDERSKRIVER_IDENT
//
//            behandlingService.setMedunderskriverFlowState(
//                behandlingId,
//                MEDUNDERSKRIVER_IDENT,
//                SAKSBEHANDLER_IDENT,
//                FlowState.SENT,
//            )
//
//            behandlingService.switchMedunderskriverFlowState(
//                behandlingId,
//                MEDUNDERSKRIVER_IDENT
//            )
//
//            behandlingService.switchMedunderskriverFlowState(
//                behandlingId,
//                MEDUNDERSKRIVER_IDENT
//            )
//
//            val output = behandlingRepository.getReferenceById(behandlingId)
//
//            assertThat(output.medunderskriverFlowState).isEqualTo(FlowState.RETURNED)
//        }
    }

    @Test
    fun `Forsøk på ferdigstilling av behandling som allerede er avsluttet av saksbehandler skal ikke lykkes`() {
        val behandling = simpleInsert(fullfoert = true)
        every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling) } returns Unit

        assertThrows<BehandlingAvsluttetException> {
            behandlingService.ferdigstillBehandling(
                behandlingId = behandling.id,
                innloggetIdent = SAKSBEHANDLER_IDENT,
                gosysOppgaveInput = null,
                nyBehandlingEtterTROpphevet = false,
            )
        }
    }

    @Nested
    inner class ValidateBehandlingBeforeFinalize {
        @Test
        fun `Forsøk på avslutting av behandling som har uferdige dokumenter skal ikke lykkes`() {
            val behandling = simpleInsert()
            every { dokumentUnderArbeidRepository.findByBehandlingIdAndMarkertFerdigIsNull(any()) } returns
                    sortedSetOf(
                        SmartdokumentUnderArbeidAsHoveddokument(
                            mellomlagerId = "",
                            mellomlagretDate = LocalDateTime.now(),
                            size = 0,
                            name = "",
                            smartEditorId = UUID.randomUUID(),
                            smartEditorTemplateId = "null",
                            behandlingId = UUID.randomUUID(),
                            dokumentType = DokumentType.VEDTAK,
                            created = LocalDateTime.now(),
                            modified = LocalDateTime.now(),
                            markertFerdig = null,
                            ferdigstilt = null,
                            dokumentEnhetId = null,
                            creatorIdent = "null",
                            creatorRole = KABAL_SAKSBEHANDLING,
                            journalfoerendeEnhetId = null,
                            language = Language.NB,
                            mellomlagretVersion = null,
                        )
                    )

            assertThrows<SectionedValidationErrorWithDetailsException> {
                behandlingService.validateBehandlingBeforeFinalize(
                    behandlingId = behandling.id,
                    nyBehandlingEtterTROpphevet = false,
                )
            }
        }

        @Test
        fun `Forsøk på avslutting av behandling som ikke har utfall skal ikke lykkes`() {
            val behandling = simpleInsert(fullfoert = false, utfall = false)

            assertThrows<SectionedValidationErrorWithDetailsException> {
                behandlingService.validateBehandlingBeforeFinalize(
                    behandlingId = behandling.id,
                    nyBehandlingEtterTROpphevet = false,
                )
            }
        }

        @Test
        fun `Forsøk på avslutting av behandling som ikke har hjemler skal ikke lykkes`() {
            val behandling =
                simpleInsert(fullfoert = false, utfall = true, hjemler = false)

            assertThrows<SectionedValidationErrorWithDetailsException> {
                behandlingService.validateBehandlingBeforeFinalize(
                    behandlingId = behandling.id,
                    nyBehandlingEtterTROpphevet = false,
                )
            }
        }

        @Test
        fun `Forsøk på avslutting av behandling som er trukket og som ikke har hjemler skal lykkes`() {
            val behandling = simpleInsert(
                fullfoert = false,
                utfall = true,
                hjemler = false,
                trukket = true
            )

            behandlingService.validateBehandlingBeforeFinalize(
                behandlingId = behandling.id,
                nyBehandlingEtterTROpphevet = false,
            )
        }
    }

    @Test
    fun `Ferdigstill behandling med både ignoreGosysOppgave og gosysOppgaveUpdateInput gir feil`() {
        assertThrows<SectionedValidationErrorWithDetailsException> {
            behandlingService.ferdigstillBehandling(
                behandlingId = UUID.randomUUID(),
                innloggetIdent = SAKSBEHANDLER_IDENT,
                gosysOppgaveInput = GosysOppgaveInput(
                    gosysOppgaveUpdate = GosysOppgaveUpdateInput(
                        tildeltEnhet = Enhet.E0119.id,
                        mappeId = 123,
                        kommentar = "Kommentar"
                    ),
                    ignoreGosysOppgave = true,
                ),
                nyBehandlingEtterTROpphevet = false,
            )
        }
    }

    private fun simpleInsert(
        fullfoert: Boolean = false,
        utfall: Boolean = true,
        hjemler: Boolean = true,
        trukket: Boolean = false
    ): Behandling {
        val now = LocalDateTime.now()

        val ferdigstilling = Ferdigstilling(
            avsluttet = now,
            avsluttetAvSaksbehandler = now,
            navIdent = "navIdent",
            navn = "navn",
        )

        val behandling = Klagebehandling(
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354")
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            frist = LocalDate.now(),
            hjemler = if (hjemler) mutableSetOf(
                Hjemmel.FTRL_8_7
            ) else mutableSetOf(),
            created = now,
            modified = now,
            mottattKlageinstans = now,
            fagsystem = Fagsystem.K9,
            fagsakId = "123",
            kildeReferanse = "abc",
            mottattVedtaksinstans = LocalDate.now(),
            avsenderEnhetFoersteinstans = "enhet",
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 2,
            utfall = when {
                trukket -> Utfall.TRUKKET
                utfall -> Utfall.AVVIST
                else -> null
            },
            extraUtfallSet = when {
                trukket -> setOf(Utfall.TRUKKET)
                utfall -> setOf(Utfall.AVVIST)
                else -> emptySet()
            },
            registreringshjemler = if (hjemler) mutableSetOf(
                Registreringshjemmel.ANDRE_TRYGDEAVTALER
            ) else mutableSetOf(),

            ferdigstilling = if (fullfoert) ferdigstilling else null,
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

        behandlingRepository.save(behandling)

        testEntityManager.flush()
        testEntityManager.clear()

        return behandling
    }
}
