package no.nav.klage.oppgave.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.metamodel.EntityType
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Sakstype
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingWithOptionalTrygderettenSaksnummer
import no.nav.klage.oppgave.domain.behandling.GjenopptakITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.exceptions.IllegalOperation
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.repositories.BehandlingRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

class TrygderettenSaksnummerServiceTest {
    private val repository = mockk<BehandlingRepository>()
    private val tilgangService = mockk<TilgangService>(relaxed = true)
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val innloggetSaksbehandlerService = mockk<InnloggetSaksbehandlerService>()
    private val entityManager = mockk<EntityManager>()
    private val service =
        BehandlingService(
            behandlingRepository = repository,
            tilgangService = tilgangService,
            applicationEventPublisher = publisher,
            kakaApiGateway = mockk(),
            dokumentService = mockk(),
            dokumentUnderArbeidRepository = mockk(),
            kabalInnstillingerService = mockk(),
            innloggetSaksbehandlerService = innloggetSaksbehandlerService,
            arbeidOgInntektClient = mockk(),
            klankeService = mockk(),
            eregClient = mockk(),
            saksbehandlerService = mockk(),
            behandlingMapper = mockk(),
            historyService = mockk(),
            kafkaInternalEventService = mockk(),
            partSearchService = mockk(),
            safFacade = mockk(),
            systembrukerIdent = "TEST",
            tokenUtil = mockk(),
            gosysOppgaveService = mockk(),
            kodeverkService = mockk(),
            klageNotificationsApiClient = mockk(),
            schedulerHealthGate = mockk(),
            entityManager = entityManager,
        )

    @Test
    fun `setter publishes changes for both optional types and checks read and write access`() {
        optionalBehandlinger().forEach { behandling ->
            prepare(behandling)

            val modified =
                service.setTrygderettenSaksnummer(
                    behandlingId = behandling.id,
                    trygderettenSaksnummer = "2026123",
                    utfoerendeSaksbehandlerIdent = "TEST",
                )

            assertThat((behandling as BehandlingWithOptionalTrygderettenSaksnummer).trygderettenSaksnummer)
                .isEqualTo("2026123")
            assertThat(modified).isEqualTo(behandling.modified).isAfter(BASELINE)
            verify(exactly = 1) {
                tilgangService.verifyLoggedInUsersAccessToPersongalleriInBehandling(behandling)
                tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling)
                publisher.publishEvent(
                    match<BehandlingChangedEvent> {
                        it.behandling === behandling &&
                            it.changeList.single().felt == BehandlingChangedEvent.Felt.TRYGDERETTEN_SAKSNUMMER
                    },
                )
            }
        }
    }

    @Test
    fun `setter clears optional number and does not publish an event for a repeated null`() {
        val behandling = optionalBehandlinger().first()
        prepare(behandling)
        (behandling as BehandlingWithOptionalTrygderettenSaksnummer).trygderettenSaksnummer = "2026123"

        val modified =
            service.setTrygderettenSaksnummer(
                behandlingId = behandling.id,
                trygderettenSaksnummer = null,
                utfoerendeSaksbehandlerIdent = "TEST",
            )
        val repeatedModified =
            service.setTrygderettenSaksnummer(
                behandlingId = behandling.id,
                trygderettenSaksnummer = null,
                utfoerendeSaksbehandlerIdent = "TEST",
            )

        assertThat(behandling.trygderettenSaksnummer).isNull()
        assertThat(repeatedModified).isEqualTo(modified)
        verify(exactly = 1) { publisher.publishEvent(any<BehandlingChangedEvent>()) }
    }

    @Test
    fun `invalid input and unsupported mandatory types do not publish events`() {
        val behandling = optionalBehandlinger().first()
        prepare(behandling)

        assertThrows<IllegalOperation> {
            service.setTrygderettenSaksnummer(
                behandlingId = behandling.id,
                trygderettenSaksnummer = "20260",
                utfoerendeSaksbehandlerIdent = "TEST",
            )
        }
        assertThat(behandling.modified).isEqualTo(BASELINE)
        assertThat((behandling as BehandlingWithOptionalTrygderettenSaksnummer).trygderettenSaksnummer).isNull()

        val mandatory = mockk<AnkebehandlingEtter2027>(relaxed = true)
        every { mandatory.feilregistrering } returns null
        every { mandatory.ferdigstilling } returns null
        prepare(mandatory)
        assertThrows<IllegalOperation> {
            service.setTrygderettenSaksnummer(
                behandlingId = mandatory.id,
                trygderettenSaksnummer = null,
                utfoerendeSaksbehandlerIdent = "TEST",
            )
        }
        verify(exactly = 0) { publisher.publishEvent(any<BehandlingChangedEvent>()) }
    }

    @Test
    fun `denied write access prevents mutation and events`() {
        val behandling = optionalBehandlinger().first()
        prepare(behandling)
        every { tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling) } throws MissingTilgangException("Test")

        assertThrows<MissingTilgangException> {
            service.setTrygderettenSaksnummer(
                behandlingId = behandling.id,
                trygderettenSaksnummer = "2026123",
                utfoerendeSaksbehandlerIdent = "TEST",
            )
        }

        assertThat((behandling as BehandlingWithOptionalTrygderettenSaksnummer).trygderettenSaksnummer).isNull()
        verify(exactly = 0) { publisher.publishEvent(any<BehandlingChangedEvent>()) }
    }

    @Test
    fun `duplicate search includes optional and mandatory entity discriminators`() {
        val anke = mockk<EntityType<AnkeITrygderettenbehandlingFoer2027>>()
        val gjenopptak = mockk<EntityType<GjenopptakITrygderettenbehandlingFoer2027>>()
        val mandatory = mockk<EntityType<AnkebehandlingEtter2027>>()
        every { anke.javaType } returns AnkeITrygderettenbehandlingFoer2027::class.java
        every { gjenopptak.javaType } returns GjenopptakITrygderettenbehandlingFoer2027::class.java
        every { mandatory.javaType } returns AnkebehandlingEtter2027::class.java
        every { entityManager.metamodel.entities } returns setOf(anke, gjenopptak, mandatory)
        every {
            repository.existsByTrygderettenSaksnummerAndFeilregistreringIsNull(
                trygderettenSaksnummer = "2026123",
                behandlingTypes =
                    match {
                        it.toSet() == setOf("anke_i_trygderetten_foer_2027", "gjenopptak_i_trygderetten", "anke_etter_2027")
                    },
            )
        } returns true

        assertThat(service.trygderettenSaksnummerIsDuplicate("2026123")).isTrue()
    }

    private fun prepare(behandling: Behandling) {
        every { repository.findById(behandling.id) } returns Optional.of(behandling)
        every { innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter() } returns false
    }

    private fun optionalBehandlinger(): List<Behandling> =
        listOf(
            AnkeITrygderettenbehandlingFoer2027(
                sendtTilTrygderetten = BASELINE,
                klager = Klager(id = UUID.randomUUID(), partId = PartId(type = PartIdType.PERSON, value = "synthetic-test")),
                sakenGjelder =
                    SakenGjelder(id = UUID.randomUUID(), partId = PartId(type = PartIdType.PERSON, value = "synthetic-test")),
                prosessfullmektig = null,
                ytelse = Ytelse.OMS_OMP,
                type = Type.ANKE_I_TRYGDERETTEN_FOER_2027,
                kildeReferanse = "synthetic-reference",
                fagsystem = Fagsystem.AO01,
                fagsakId = null,
                sakstype = Sakstype.GENERELL_SAK,
                arkivsaksnummer = "TEST",
                mottattKlageinstans = BASELINE,
                modified = BASELINE,
                previousSaksbehandlerident = null,
                gosysOppgaveId = null,
                gosysOppgaveRequired = false,
                initiatingSystem = Behandling.InitiatingSystem.KABAL,
                previousBehandlingId = null,
            ),
            GjenopptakITrygderettenbehandlingFoer2027(
                sendtTilTrygderetten = BASELINE,
                klager = Klager(id = UUID.randomUUID(), partId = PartId(type = PartIdType.PERSON, value = "synthetic-test")),
                sakenGjelder =
                    SakenGjelder(id = UUID.randomUUID(), partId = PartId(type = PartIdType.PERSON, value = "synthetic-test")),
                prosessfullmektig = null,
                ytelse = Ytelse.OMS_OMP,
                type = Type.BEGJAERING_OM_GJENOPPTAK_I_TRYGDERETTEN_FOER_2027,
                kildeReferanse = "synthetic-reference",
                fagsystem = Fagsystem.AO01,
                fagsakId = null,
                sakstype = Sakstype.GENERELL_SAK,
                arkivsaksnummer = "TEST",
                mottattKlageinstans = BASELINE,
                modified = BASELINE,
                previousSaksbehandlerident = null,
                gosysOppgaveId = null,
                gosysOppgaveRequired = false,
                initiatingSystem = Behandling.InitiatingSystem.KABAL,
                previousBehandlingId = null,
            ),
        )

    private companion object {
        val BASELINE: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
