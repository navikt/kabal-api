package no.nav.klage.oppgave.api.mapper

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.klage.kodeverk.*
import no.nav.klage.oppgave.api.view.SaksbehandlerView
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.repositories.SaksbehandlerRepository
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
    lateinit var saksbehandlerRepository: SaksbehandlerRepository

    @MockkBean
    lateinit var kabalDocumentGateway: KabalDocumentGateway

    @Autowired
    lateinit var behandlingMapper: BehandlingMapper

    private val FNR = "FNR"
    private val MEDUNDERSKRIVER_IDENT = "MEDUNDERSKRIVER_IDENT"
    private val MEDUNDERSKRIVER_NAVN = "MEDUNDERSKRIVER_NAVN"

    @Test
    fun `mapToMedunderskriverView og mapToMedunderskriverFlytView gir forventet resultat når medunderskriver og medunderskriverFlyt ikke er satt`() {
        val klagebehandling = getKlagebehandling()
        val viewResult = behandlingMapper.mapToMedunderskriverView(klagebehandling)
        val flytViewResult = behandlingMapper.mapToMedunderskriverFlytView(klagebehandling)

        assertThat(viewResult.medunderskriver).isNull()
        assertThat(flytViewResult.medunderskriverFlyt).isEqualTo(MedunderskriverFlyt.IKKE_SENDT)
    }

    @Test
    fun `mapToMedunderskriverInfoView og mapToMedunderskriverFlytView gir forventet resultat når medunderskriver og medunderskriverFlyt er satt`() {
        val klagebehandling = getKlagebehandlingWithMedunderskriver()
        every { saksbehandlerRepository.getNameForSaksbehandler(any()) } returns MEDUNDERSKRIVER_NAVN

        val viewResult = behandlingMapper.mapToMedunderskriverView(klagebehandling)
        val flytViewResult = behandlingMapper.mapToMedunderskriverFlytView(klagebehandling)

        assertThat(viewResult.medunderskriver).isEqualTo(
            SaksbehandlerView(
                MEDUNDERSKRIVER_IDENT,
                MEDUNDERSKRIVER_NAVN
            )
        )
        assertThat(flytViewResult.medunderskriverFlyt).isEqualTo(MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER)
    }

    private fun getKlagebehandling(): Klagebehandling {
        return Klagebehandling(
            sakFagsystem = Fagsystem.AO01,
            kildeReferanse = "abc",
            klager = Klager(PartId(PartIdType.PERSON, FNR)),
            sakenGjelder = SakenGjelder(PartId(PartIdType.PERSON, FNR), false),
            mottakId = UUID.randomUUID(),
            mottattKlageinstans = LocalDateTime.now(),
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            avsenderEnhetFoersteinstans = "4100",
            mottattVedtaksinstans = LocalDate.now(),
            delbehandlinger = setOf(Delbehandling()),
        )
    }

    private fun getKlagebehandlingWithMedunderskriver(): Klagebehandling {
        return getKlagebehandling().apply {
            currentDelbehandling().medunderskriver = MedunderskriverTildeling(
                MEDUNDERSKRIVER_IDENT,
                LocalDateTime.now()
            )
            currentDelbehandling().medunderskriverFlyt = MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER
        }
    }
}