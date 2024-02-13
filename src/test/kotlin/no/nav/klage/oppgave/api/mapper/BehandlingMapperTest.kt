package no.nav.klage.oppgave.api.mapper

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.klage.kodeverk.*
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.krrproxy.KrrProxyClient
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.service.KodeverkService
import no.nav.klage.oppgave.service.RegoppslagService
import no.nav.klage.oppgave.service.SaksbehandlerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@ActiveProfiles("local")
@SpringBootTest(classes = [BehandlingMapper::class])
class BehandlingMapperTest {
    @MockkBean
    lateinit var pdlFacade: PdlFacade

    @MockkBean
    lateinit var egenAnsattService: EgenAnsattService

    @MockkBean
    lateinit var norg2Client: Norg2Client

    @MockkBean
    lateinit var eregClient: EregClient

    @MockkBean
    lateinit var saksbehandlerService: SaksbehandlerService

    @MockkBean
    lateinit var kabalDocumentGateway: KabalDocumentGateway

    @MockkBean
    lateinit var krrProxyClient: KrrProxyClient

    @MockkBean
    lateinit var kodeverkService: KodeverkService

    @MockkBean
    lateinit var regoppslagService: RegoppslagService

    @Autowired
    lateinit var behandlingMapper: BehandlingMapper

    private val FNR = "FNR"
    private val MEDUNDERSKRIVER_IDENT = "MEDUNDERSKRIVER_IDENT"
    private val MEDUNDERSKRIVER_NAVN = "MEDUNDERSKRIVER_NAVN"

    @Test
    fun `mapToMedunderskriverWrapped og mapToMedunderskriverFlowStateView gir forventet resultat når medunderskriver og medunderskriverFlowState ikke er satt`() {
        val klagebehandling = getKlagebehandling()
        val viewResult = behandlingMapper.mapToMedunderskriverWrapped(klagebehandling)
        val flytViewResult = behandlingMapper.mapToMedunderskriverFlowStateView(klagebehandling)

        assertThat(viewResult.employee).isNull()
        assertThat(flytViewResult.flowState).isEqualTo(FlowState.NOT_SENT)
    }

    @Test
    fun `mapToMedunderskriverWrapped og mapToMedunderskriverFlowStateView gir forventet resultat når medunderskriver og medunderskriverFlowState er satt`() {
        val klagebehandling = getKlagebehandlingWithMedunderskriver()
        every { saksbehandlerService.getNameForIdentDefaultIfNull(any()) } returns MEDUNDERSKRIVER_NAVN

        val viewResult = behandlingMapper.mapToMedunderskriverWrapped(klagebehandling)
        val flytViewResult = behandlingMapper.mapToMedunderskriverFlowStateView(klagebehandling)

        assertThat(viewResult.employee?.navIdent).isEqualTo(MEDUNDERSKRIVER_IDENT)
        assertThat(flytViewResult.flowState).isEqualTo(FlowState.SENT)
    }

    private fun getKlagebehandling(): Klagebehandling {
        return Klagebehandling(
            fagsystem = Fagsystem.AO01,
            fagsakId = "123",
            kildeReferanse = "abc",
            klager = Klager(PartId(PartIdType.PERSON, FNR)),
            sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, FNR), false),
            mottakId = UUID.randomUUID(),
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            avsenderEnhetFoersteinstans = "4100",
            mottattVedtaksinstans = LocalDate.now(),
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 2,
            frist = LocalDate.now().plusWeeks(12),
            previousSaksbehandlerident = "C78901",
        )
    }

    private fun getKlagebehandlingWithMedunderskriver(): Klagebehandling {
        return getKlagebehandling().apply {
            medunderskriver = MedunderskriverTildeling(
                MEDUNDERSKRIVER_IDENT,
                LocalDateTime.now()
            )
            medunderskriverFlowState = FlowState.SENT
        }
    }
}