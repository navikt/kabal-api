package no.nav.klage.oppgave.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.klage.oppgave.api.view.VedtakFullfoerInput
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.kodeverk.*
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.exceptions.VedtakFinalizedException
import no.nav.klage.oppgave.exceptions.VedtakNotFoundException
import no.nav.klage.oppgave.gateway.JournalpostGateway
import no.nav.klage.oppgave.util.AttachmentValidator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@ActiveProfiles("local")
@Testcontainers
@SpringBootTest(classes = [KlagebehandlingService::class])
class VedtakServiceTest {

    @MockkBean
    lateinit var tilgangService: TilgangService

    @MockkBean(relaxed = true)
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @MockkBean
    lateinit var klagebehandlingService: KlagebehandlingService

    @MockkBean
    lateinit var attachmentValidator: AttachmentValidator

    @MockkBean
    lateinit var fileApiService: FileApiService

    @MockkBean
    lateinit var journalpostGateway: JournalpostGateway

    lateinit var vedtakService: VedtakService

    @BeforeEach
    fun setup() {
        vedtakService = VedtakService(
            klagebehandlingService,
            applicationEventPublisher,
            attachmentValidator,
            tilgangService,
            fileApiService,
            journalpostGateway
        )
    }

    private val SAKSBEHANDLER_IDENT = "SAKSBEHANDLER_IDENT"
    private val MEDUNDERSKRIVER_IDENT = "MEDUNDERSKRIVER_IDENT"
    private val JOURNALFOERENDE_ENHET = "1234"
    private val MELLOMLAGER_ID = "MELLOMLAGER_ID"
    private val KLAGEBEHANDLING_ID = UUID.randomUUID()


    @Test
    fun `Forsøk på avslutting av vedtak fra andre enn medunderskriver skal ikke lykkes`() {
        every { klagebehandlingService.getKlagebehandlingForUpdate(any(), any(), any()) } returns getKlageBehandling()
        assertThrows<MissingTilgangException> {
            vedtakService.ferdigstillVedtak(
                KLAGEBEHANDLING_ID,
                VedtakFullfoerInput(
                    JOURNALFOERENDE_ENHET,
                    1L
                ),
                SAKSBEHANDLER_IDENT
            )
        }
    }

    @Test
    fun `Forsøk på avslutting av vedtak som allerede er ferdigstilt i Joark skal ikke lykkes`() {
        every {
            klagebehandlingService.getKlagebehandlingForUpdate(
                any(),
                any(),
                any()
            )
        } returns getFerdigstiltKlagebehandling()
        assertThrows<VedtakFinalizedException> {
            vedtakService.ferdigstillVedtak(
                KLAGEBEHANDLING_ID,
                VedtakFullfoerInput(
                    JOURNALFOERENDE_ENHET,
                    1L
                ),
                MEDUNDERSKRIVER_IDENT
            )
        }
    }

    @Test
    fun `Forsøk på avslutting av vedtak som ikke har mellomlagret dokument skal ikke lykkes`() {
        every { klagebehandlingService.getKlagebehandlingForUpdate(any(), any(), any()) } returns getKlageBehandling()
        assertThrows<VedtakNotFoundException> {
            vedtakService.ferdigstillVedtak(
                KLAGEBEHANDLING_ID,
                VedtakFullfoerInput(
                    JOURNALFOERENDE_ENHET,
                    1L
                ),
                MEDUNDERSKRIVER_IDENT
            )
        }
    }

    @Test
    fun `Forsøk på avslutting av vedtak som ikke har utfall skal ikke lykkes`() {
        every { klagebehandlingService.getKlagebehandlingForUpdate(any(), any(), any()) } returns getKlageBehandling()
        assertThrows<VedtakNotFoundException> {
            vedtakService.ferdigstillVedtak(
                KLAGEBEHANDLING_ID,
                VedtakFullfoerInput(
                    JOURNALFOERENDE_ENHET,
                    1L
                ),
                MEDUNDERSKRIVER_IDENT
            )
        }
    }

    @Test
    fun `Forsøk på avslutting av vedtak som er riktig utfylt skal lykkes`() {
        every {
            klagebehandlingService.getKlagebehandlingForUpdate(
                any(),
                any(),
                any()
            )
        } returns getKlagebehandlingMedUtfall()
        every {
            klagebehandlingService.markerKlagebehandlingSomAvsluttetAvSaksbehandler(
                any(),
                any()
            )
        } returns getKlagebehandlingMedUtfall()
        val result = vedtakService.ferdigstillVedtak(
            KLAGEBEHANDLING_ID,
            VedtakFullfoerInput(
                JOURNALFOERENDE_ENHET,
                1L
            ),
            MEDUNDERSKRIVER_IDENT
        )
        assert(result.getVedtakOrException().avsluttetAvSaksbehandler != null)
    }

    private fun getKlageBehandling(): Klagebehandling {
        return Klagebehandling(
            versjon = 2L,
            klager = Klager(partId = PartId(type = PartIdType.PERSON, value = "23452354")),
            sakenGjelder = SakenGjelder(
                partId = PartId(type = PartIdType.PERSON, value = "23452354"),
                skalMottaKopi = false
            ),
            tema = Tema.OMS,
            type = Type.KLAGE,
            frist = LocalDate.now(),
            hjemler = mutableSetOf(
                Hjemmel.FTL_8_7
            ),
            created = LocalDateTime.now(),
            modified = LocalDateTime.now(),
            mottattKlageinstans = LocalDateTime.now(),
            kildesystem = Fagsystem.K9,
            mottakId = UUID.randomUUID(),
            vedtak = Vedtak(
                hjemler = mutableSetOf(
                    Hjemmel.FTL
                )
            ),
            medunderskriver = MedunderskriverTildeling(
                MEDUNDERSKRIVER_IDENT,
                LocalDateTime.now()
            )
        )
    }

    private fun getFerdigstiltKlagebehandling(): Klagebehandling {
        return getKlageBehandling().apply { getVedtakOrException().ferdigstiltIJoark = LocalDateTime.now() }
    }

    private fun getKlagebehandlingMedUtfall(): Klagebehandling {
        return getKlageBehandling().apply {
            getVedtakOrException().utfall = Utfall.AVVIST
            getVedtakOrException().mellomlagerId = MELLOMLAGER_ID
        }
    }
}