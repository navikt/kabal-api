package no.nav.klage.oppgave.service

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeid
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.MedunderskriverFlowStateResponse
import no.nav.klage.oppgave.api.view.MedunderskriverWrapped
import no.nav.klage.oppgave.clients.arbeidoginntekt.ArbeidOgInntektClient
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.db.TestPostgresqlContainer
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.klage.BehandlingRole.KABAL_SAKSBEHANDLING
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.exceptions.BehandlingFinalizedException
import no.nav.klage.oppgave.exceptions.SectionedValidationErrorWithDetailsException
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.MottakRepository
import no.nav.klage.oppgave.repositories.SaksbehandlerRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.Collections.emptySortedSet

@ActiveProfiles("local")
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BehandlingServiceTest {

    companion object {
        @Container
        @JvmField
        val postgreSQLContainer: TestPostgresqlContainer = TestPostgresqlContainer.instance
    }

    @Autowired
    lateinit var testEntityManager: TestEntityManager

    @Autowired
    lateinit var mottakRepository: MottakRepository

    @Autowired
    lateinit var behandlingRepository: BehandlingRepository

    @SpykBean
    lateinit var tilgangService: TilgangService

    @MockkBean
    lateinit var innloggetSaksbehandlerService: InnloggetSaksbehandlerService

    @MockkBean(relaxed = true)
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockkBean
    lateinit var pdlFacade: PdlFacade

    @MockkBean
    lateinit var egenAnsattService: EgenAnsattService

    @MockkBean
    lateinit var saksbehandlerRepository: SaksbehandlerRepository

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

    lateinit var behandlingService: BehandlingService

    private val SAKSBEHANDLER_IDENT = "SAKSBEHANDLER_IDENT"
    private val MEDUNDERSKRIVER_IDENT = "MEDUNDERSKRIVER_IDENT"
    private val DOKUMENTENHET_ID = UUID.randomUUID()

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
            saksbehandlerRepository = saksbehandlerRepository,
            eregClient = eregClient,
            saksbehandlerService = saksbehandlerService,
            behandlingMapper = behandlingMapper,
        )
        every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling) } returns Unit
        every { innloggetSaksbehandlerService.getInnloggetIdent() } returns SAKSBEHANDLER_IDENT
        every { tilgangService.verifyInnloggetSaksbehandlersTilgangTil(any()) } returns Unit
        every { tilgangService.harInnloggetSaksbehandlerTilgangTil(any()) } returns true
        every { saksbehandlerRepository.hasKabalOppgavestyringAlleEnheterRole(any()) } returns false
        every { behandlingMapper.mapToMedunderskriverWrapped(any()) } returns MedunderskriverWrapped(
            navIdent = "null",
            modified = LocalDateTime.now(),
            flowState = FlowState.SENT,
        )
        every { behandlingMapper.mapToMedunderskriverFlowStateResponse(any()) } returns MedunderskriverFlowStateResponse(
            navn = null,
            navIdent = null,
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

        assertThrows<BehandlingFinalizedException> {
            behandlingService.ferdigstillBehandling(
                behandlingId = behandling.id,
                innloggetIdent = SAKSBEHANDLER_IDENT,
                nyBehandling = false,
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
                        DokumentUnderArbeid(
                            mellomlagerId = "",
                            opplastet = LocalDateTime.now(),
                            size = 0,
                            name = "",
                            smartEditorId = null,
                            smartEditorTemplateId = null,
                            behandlingId = UUID.randomUUID(),
                            dokumentType = DokumentType.VEDTAK,
                            created = LocalDateTime.now(),
                            modified = LocalDateTime.now(),
                            markertFerdig = null,
                            ferdigstilt = null,
                            dokumentEnhetId = null,
                            parentId = null,
                            journalfoertDokumentReference = null,
                            creatorIdent = "null",
                            creatorRole = KABAL_SAKSBEHANDLING,
                        )
                    )

            assertThrows<SectionedValidationErrorWithDetailsException> {
                behandlingService.validateBehandlingBeforeFinalize(behandling.id, nyBehandling = false,)
            }
        }

        @Test
        fun `Forsøk på avslutting av behandling som ikke har utfall skal ikke lykkes`() {
            val behandling = simpleInsert(fullfoert = false, utfall = false)

            assertThrows<SectionedValidationErrorWithDetailsException> {
                behandlingService.validateBehandlingBeforeFinalize(behandling.id, nyBehandling = false,)
            }
        }

        @Test
        fun `Forsøk på avslutting av behandling som ikke har hjemler skal ikke lykkes`() {
            val behandling =
                simpleInsert(fullfoert = false, utfall = true, hjemler = false)

            assertThrows<SectionedValidationErrorWithDetailsException> {
                behandlingService.validateBehandlingBeforeFinalize(behandling.id, nyBehandling = false,)
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

            behandlingService.validateBehandlingBeforeFinalize(behandling.id, nyBehandling = false,)
        }
    }

    private fun simpleInsert(
        fullfoert: Boolean = false,
        utfall: Boolean = true,
        hjemler: Boolean = true,
        trukket: Boolean = false
    ): Behandling {
        val mottak = Mottak(
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
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

        val behandling = Klagebehandling(
            klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
            sakenGjelder = SakenGjelder(
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                skalMottaKopi = false
            ),
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
            mottakId = mottak.id,
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
            avsluttetAvSaksbehandler = if (fullfoert) LocalDateTime.now() else null,
        )

        behandlingRepository.save(behandling)

        testEntityManager.flush()
        testEntityManager.clear()

        return behandling
    }
}
