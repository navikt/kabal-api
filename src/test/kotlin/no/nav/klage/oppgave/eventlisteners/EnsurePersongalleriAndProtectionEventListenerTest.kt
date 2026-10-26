package no.nav.klage.oppgave.eventlisteners

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Sakstype
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.repositories.SakPersongalleriRepository
import no.nav.klage.oppgave.service.PersonProtectionService
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class EnsurePersongalleriAndProtectionEventListenerTest {
    private val personProtectionService = mockk<PersonProtectionService>(relaxed = true)
    private val sakPersongalleriRepository = mockk<SakPersongalleriRepository>(relaxed = true)
    private val klageLookupGateway = mockk<KlageLookupGateway>(relaxed = true)

    private val listener =
        EnsurePersongalleriAndProtectionEventListener(
            personProtectionService = personProtectionService,
            sakPersongalleriRepository = sakPersongalleriRepository,
            klageLookupGateway = klageLookupGateway,
        )

    private val sakenGjelderIdent = "23452354"

    @Test
    fun `generell sak on FS36 skips persongalleri`() {
        listener.ensurePersongalleriAndProtectionOnBehandlingCreated(
            createdEvent(klagebehandling(fagsakId = null, sakstype = Sakstype.GENERELL_SAK)),
        )

        verify(exactly = 1) { personProtectionService.createPersonProtection(foedselsnummer = sakenGjelderIdent) }
        verify(exactly = 0) { klageLookupGateway.getPersongalleri(any()) }
        verify(exactly = 0) { sakPersongalleriRepository.findByFagsystemAndFagsakId(fagsystem = any(), fagsakId = any()) }
        verify(exactly = 0) { sakPersongalleriRepository.save(any()) }
    }

    @Test
    fun `generell sak on FS36 with fagsakId still skips persongalleri`() {
        listener.ensurePersongalleriAndProtectionOnBehandlingCreated(
            createdEvent(klagebehandling(fagsakId = "123", sakstype = Sakstype.GENERELL_SAK)),
        )

        verify(exactly = 0) { klageLookupGateway.getPersongalleri(any()) }
        verify(exactly = 0) { sakPersongalleriRepository.findByFagsystemAndFagsakId(fagsystem = any(), fagsakId = any()) }
    }

    @Test
    fun `fagsak on FS36 populates persongalleri`() {
        every { sakPersongalleriRepository.findByFagsystemAndFagsakId(fagsystem = Fagsystem.FS36, fagsakId = "123") } returns emptyList()
        every { klageLookupGateway.getPersongalleri(any()) } returns emptyList()

        listener.ensurePersongalleriAndProtectionOnBehandlingCreated(
            createdEvent(klagebehandling(fagsakId = "123", sakstype = Sakstype.FAGSAK)),
        )

        verify(exactly = 1) { sakPersongalleriRepository.findByFagsystemAndFagsakId(fagsystem = Fagsystem.FS36, fagsakId = "123") }
        verify(exactly = 1) { klageLookupGateway.getPersongalleri(any()) }
    }

    private fun createdEvent(behandling: Behandling) =
        BehandlingChangedEvent(
            behandling = behandling,
            changeList =
                listOf(
                    BehandlingChangedEvent.Change(
                        saksbehandlerident = null,
                        felt = BehandlingChangedEvent.Felt.KLAGEBEHANDLING_OPPRETTET,
                        behandlingId = behandling.id,
                    ),
                ),
        )

    private fun klagebehandling(
        fagsakId: String?,
        sakstype: Sakstype,
    ): Klagebehandling {
        val sakenGjelder = SakenGjelder(id = UUID.randomUUID(), partId = PartId(type = PartIdType.PERSON, value = sakenGjelderIdent))
        return Klagebehandling(
            mottattVedtaksinstans = LocalDate.now(),
            previousSaksbehandlerident = null,
            avsenderEnhetFoersteinstans = "",
            kommentarFraFoersteinstans = null,
            klager = Klager(id = sakenGjelder.id, partId = sakenGjelder.partId),
            sakenGjelder = sakenGjelder,
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            kildeReferanse = "abc",
            dvhReferanse = null,
            fagsystem = Fagsystem.FS36,
            fagsakId = fagsakId,
            sakstype = sakstype,
            arkivsaksnummer = if (sakstype == Sakstype.GENERELL_SAK) "140411735" else null,
            mottattKlageinstans = LocalDateTime.now(),
            frist = LocalDate.now(),
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 2,
            hjemler = setOf(),
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )
    }
}
