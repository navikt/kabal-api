package no.nav.klage.innsyn.service

import io.mockk.every
import io.mockk.mockk
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.behandling.Ankebehandling
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Ferdigstilling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.repositories.BehandlingRepository
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.*

internal class InnsynServiceTest {
    private val behandlingRepository = mockk<BehandlingRepository>()
    private val documentService = mockk<DocumentService>()
    private val innsynService: InnsynService =
        InnsynService(
            behandlingRepository = behandlingRepository,
            documentService = documentService,
        )

    @Test
    fun test() {
        every { behandlingRepository.findBySakenGjelderPartIdValueAndFeilregistreringIsNull(any()) } returns getBehandlinger()
        every { documentService.getSvarbrev(any()) } returns null
        val response = innsynService.getSakerForBruker("123")
        response.saker.forEach { sak ->
            println(sak.saksnummer)
            sak.events.forEach { event ->
                println(event)
            }
            println()
        }
    }

    private fun getBehandlinger(): List<Behandling> {
        val klageMottattVedtaksinstans = LocalDate.of(2024, 1, 1)
        val klageMottattKA = LocalDate.of(2024, 2, 1).atStartOfDay()
        val ankeMottattKA = LocalDate.of(2024, 3, 1).atStartOfDay()
        val kjennelseMottattFraTR = LocalDate.of(2024, 4, 1).atStartOfDay()

        val klagebehandling = Klagebehandling(
            mottattVedtaksinstans = klageMottattVedtaksinstans,
            avsenderEnhetFoersteinstans = "1200",
            kakaKvalitetsvurderingId = null,
            kakaKvalitetsvurderingVersion = 0,
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.PERSON,
                    value = "123"
                )
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.PERSON,
                    value = "123"
                )
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.FOR_FOR,
            type = Type.KLAGE,
            kildeReferanse = "abc",
            fagsystem = Fagsystem.FS36,
            fagsakId = "sak1",
            mottattKlageinstans = klageMottattKA,
            frist = klageMottattKA.plusWeeks(12).toLocalDate(),
            hjemler = setOf(),
            utfall = Utfall.STADFESTELSE,
            ferdigstilling = Ferdigstilling(
                avsluttet = klageMottattKA.plusDays(1),
                avsluttetAvSaksbehandler = klageMottattKA.plusDays(1),
                navIdent = "abc",
                navn = "abc"
            ),
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
            )

        val ankebehandling = Ankebehandling(
            kakaKvalitetsvurderingId = null,
            kakaKvalitetsvurderingVersion = 0,
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.PERSON,
                    value = "123"
                )
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.PERSON,
                    value = "123"
                )
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.FOR_FOR,
            type = Type.KLAGE,
            kildeReferanse = "abc",
            fagsystem = Fagsystem.FS36,
            fagsakId = "sak1",
            mottattKlageinstans = ankeMottattKA,
            frist = ankeMottattKA.plusWeeks(12).toLocalDate(),
            hjemler = setOf(),
            utfall = Utfall.STADFESTELSE,
            ferdigstilling = Ferdigstilling(
                avsluttet = ankeMottattKA.plusDays(1),
                avsluttetAvSaksbehandler = ankeMottattKA.plusDays(1),
                navIdent = "abc",
                navn = "abc"
            ),
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            klageBehandlendeEnhet = "4295",
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

        val ankeITRBehandling = AnkeITrygderettenbehandling(
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.PERSON,
                    value = "123"
                )
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.PERSON,
                    value = "123"
                )
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.FOR_FOR,
            type = Type.KLAGE,
            kildeReferanse = "abc",
            fagsystem = Fagsystem.FS36,
            fagsakId = "sak1",
            mottattKlageinstans = ankeMottattKA,
            frist = ankeMottattKA.plusWeeks(12).toLocalDate(),
            hjemler = setOf(),
            utfall = Utfall.OPPHEVET,
            nyBehandlingEtterTROpphevet = kjennelseMottattFraTR.plusDays(1),
            ferdigstilling = Ferdigstilling(
                avsluttet = kjennelseMottattFraTR.plusDays(1),
                avsluttetAvSaksbehandler = kjennelseMottattFraTR.plusDays(1),
                navIdent = "abc",
                navn = "abc"
            ),
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            sendtTilTrygderetten = ankeMottattKA.plusDays(1),
            kjennelseMottatt = kjennelseMottattFraTR,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

        val ankebehandling2KA = Ankebehandling(
            kakaKvalitetsvurderingId = null,
            kakaKvalitetsvurderingVersion = 0,
            klager = Klager(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.PERSON,
                    value = "123"
                )
            ),
            sakenGjelder = SakenGjelder(
                id = UUID.randomUUID(),
                partId = PartId(
                    type = PartIdType.PERSON,
                    value = "123"
                )
            ),
            prosessfullmektig = null,
            ytelse = Ytelse.FOR_FOR,
            type = Type.KLAGE,
            kildeReferanse = "abc",
            fagsystem = Fagsystem.FS36,
            fagsakId = "sak1",
            mottattKlageinstans = ankeMottattKA,
            frist = ankeMottattKA.plusWeeks(12).toLocalDate(),
            hjemler = setOf(),
            utfall = Utfall.STADFESTELSE,
            ferdigstilling = Ferdigstilling(
                avsluttet = kjennelseMottattFraTR.plusDays(10),
                avsluttetAvSaksbehandler = kjennelseMottattFraTR.plusDays(10),
                navIdent = "abc",
                navn = "abc"
            ),
            previousSaksbehandlerident = null,
            gosysOppgaveId = null,
            klageBehandlendeEnhet = "4295",
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

        return listOf(
            klagebehandling,
            ankebehandling,
            ankeITRBehandling,
            ankebehandling2KA,
        )
    }

}