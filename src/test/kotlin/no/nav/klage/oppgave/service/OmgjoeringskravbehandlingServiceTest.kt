package no.nav.klage.oppgave.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Sakstype
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.clients.kaka.model.response.KakaOutput
import no.nav.klage.oppgave.domain.behandling.Omgjoeringskravbehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.behandling.subentities.MottakDokumentDTO
import no.nav.klage.oppgave.domain.behandling.subentities.MottakDokumentType
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.mottak.Mottak
import no.nav.klage.oppgave.repositories.OmgjoeringskravbehandlingRepository
import no.nav.klage.oppgave.util.KakaVersionUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class OmgjoeringskravbehandlingServiceTest {
    private val repository = mockk<OmgjoeringskravbehandlingRepository>()
    private val dokumentService = mockk<DokumentService>()
    private val behandlingService = mockk<BehandlingService>(relaxed = true)
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val kakaApiGateway = mockk<KakaApiGateway>()
    private val kakaVersionUtil = mockk<KakaVersionUtil>()
    private val kvalitetsvurderingId = UUID.randomUUID()

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `begge Mottak-opprinnelser oppretter konkret omgjoeringskrav med tidligere lenke og dokumenthendelser`(
        isBasedOnJournalpost: Boolean,
    ) {
        val previousBehandlingId = UUID.randomUUID()
        val journalpostId = "journalpost-123"
        val mottak =
            Mottak(
                type = Type.OMGJOERINGSKRAV,
                klager = klager(),
                prosessfullmektig = null,
                sakenGjelder = sakenGjelder(),
                fagsystem = Fagsystem.K9,
                fagsakId = "case-id",
                sakstype = Sakstype.FAGSAK,
                arkivsaksnummer = null,
                kildeReferanse = "source-ref",
                dvhReferanse = null,
                hjemler = emptySet(),
                forrigeSaksbehandlerident = "Z12345",
                forrigeBehandlendeEnhet = "4219",
                brukersKlageMottattVedtaksinstans = null,
                mottakDokument =
                    mutableSetOf(
                        MottakDokumentDTO(
                            type = MottakDokumentType.BRUKERS_OMGJOERINGSKRAV,
                            journalpostId = journalpostId,
                        ),
                    ),
                sakMottattKaDato = LocalDateTime.of(2024, 1, 1, 0, 0),
                frist = LocalDate.of(2024, 4, 1),
                ytelse = Ytelse.OMS_OMP,
                kommentar = null,
                forrigeBehandlingId = previousBehandlingId,
                sentFrom = Mottak.Sender.KABIN,
                isBasedOnJournalpost = isBasedOnJournalpost,
                gosysOppgaveRequired = false,
                gosysOppgaveId = null,
                trygderettenSaksnummer = null,
            )
        val service =
            OmgjoeringskravbehandlingService(
                omgjoeringskravbehandlingRepository = repository,
                dokumentService = dokumentService,
                behandlingService = behandlingService,
                applicationEventPublisher = eventPublisher,
                kakaApiGateway = kakaApiGateway,
                systembrukerIdent = "SYSTEMBRUKER",
                kakaVersionUtil = kakaVersionUtil,
            )

        every { kakaVersionUtil.getKakaVersion() } returns 2
        every { kakaApiGateway.createKvalitetsvurdering(2) } returns
            KakaOutput(kvalitetsvurderingId = kvalitetsvurderingId, kvalitetsvurderingVersion = 2)
        every { dokumentService.createSaksdokumenterFromJournalpostIdList(listOf(journalpostId)) } returns
            mutableSetOf()
        every { repository.save(any<Omgjoeringskravbehandling>()) } answers { firstArg() }

        val result =
            service.createOmgjoeringskravbehandlingFromMottak(mottak) as Omgjoeringskravbehandling

        assertThat(result).isExactlyInstanceOf(Omgjoeringskravbehandling::class.java)
        assertThat(result.previousBehandlingId).isEqualTo(previousBehandlingId)
        assertThat(result.opprettetSendt).isTrue()
        assertThat(result.mottakDokument).hasSize(1)
        assertThat(result.mottakDokument.single().journalpostId).isEqualTo(journalpostId)
        assertThat(result.mottakDokument.single().type).isEqualTo(MottakDokumentType.BRUKERS_OMGJOERINGSKRAV)
        verify(exactly = 1) {
            dokumentService.createSaksdokumenterFromJournalpostIdList(listOf(journalpostId))
        }
        verify(exactly = 2) { eventPublisher.publishEvent(any<BehandlingChangedEvent>()) }
        verify(exactly = 1) {
            behandlingService.connectDocumentsFromPreviousBehandlingToBehandling(
                behandlingId = result.id,
                saksbehandlerIdent = "SYSTEMBRUKER",
                systemUserContext = true,
                ignoreCheckSkrivetilgang = true,
            )
        }
    }

    private fun klager() =
        Klager(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.PERSON, value = "01019012345"),
        )

    private fun sakenGjelder() =
        SakenGjelder(
            id = UUID.randomUUID(),
            partId = PartId(type = PartIdType.PERSON, value = "01019012345"),
        )
}
