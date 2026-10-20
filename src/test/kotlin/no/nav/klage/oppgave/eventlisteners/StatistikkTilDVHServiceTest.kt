package no.nav.klage.oppgave.eventlisteners

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Sakstype
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.AnkebehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.Klagebehandling
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.service.StatistikkTilDVHService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class StatistikkTilDVHServiceTest {
    private val kafkaEventRepository: KafkaEventRepository = mockk()

    private val statistikkTilDVHService =
        StatistikkTilDVHService(
            kafkaEventRepository = kafkaEventRepository,
        )

    @Test
    fun `shouldSendStats new behandling`() {
        val klagebehandlingChangedEvent =
            BehandlingChangedEvent(
                behandling = klagebehandlingOMP,
                changeList =
                    listOf(
                        BehandlingChangedEvent.Change(
                            saksbehandlerident = null,
                            felt = BehandlingChangedEvent.Felt.KLAGEBEHANDLING_MOTTATT,
                            behandlingId = UUID.randomUUID(),
                        ),
                    ),
            )

        val ankebehandlingChangedEvent =
            BehandlingChangedEvent(
                behandling = ankebehandlingOMP,
                changeList =
                    listOf(
                        BehandlingChangedEvent.Change(
                            saksbehandlerident = null,
                            felt = BehandlingChangedEvent.Felt.ANKEBEHANDLING_MOTTATT,
                            behandlingId = UUID.randomUUID(),
                        ),
                    ),
            )

        val ankeITrygderettenbehandlingChangedEvent =
            BehandlingChangedEvent(
                behandling = ankeITrygderettenbehandlingOMP,
                changeList =
                    listOf(
                        BehandlingChangedEvent.Change(
                            saksbehandlerident = null,
                            felt = BehandlingChangedEvent.Felt.ANKE_I_TRYGDERETTEN_OPPRETTET,
                            behandlingId = UUID.randomUUID(),
                        ),
                    ),
            )

        assertTrue(statistikkTilDVHService.shouldSendStats(klagebehandlingChangedEvent))
        assertTrue(statistikkTilDVHService.shouldSendStats(ankebehandlingChangedEvent))
        assertFalse(statistikkTilDVHService.shouldSendStats(ankeITrygderettenbehandlingChangedEvent))
    }

    @Test
    fun `shouldSendStats existing behandling`() {
        val klagebehandlingChangedEvent =
            BehandlingChangedEvent(
                behandling = klagebehandlingOMP,
                changeList =
                    listOf(
                        BehandlingChangedEvent.Change(
                            saksbehandlerident = null,
                            felt = BehandlingChangedEvent.Felt.TILDELT_SAKSBEHANDLERIDENT,
                            behandlingId = UUID.randomUUID(),
                        ),
                    ),
            )

        val klagebehandlingHJEEndretEvent =
            BehandlingChangedEvent(
                behandling = klagebehandlingHJE,
                changeList =
                    listOf(
                        BehandlingChangedEvent.Change(
                            saksbehandlerident = null,
                            felt = BehandlingChangedEvent.Felt.TILDELT_SAKSBEHANDLERIDENT,
                            behandlingId = UUID.randomUUID(),
                        ),
                    ),
            )

        val ankebehandlingChangedEvent =
            BehandlingChangedEvent(
                behandling = ankebehandlingOMP,
                changeList =
                    listOf(
                        BehandlingChangedEvent.Change(
                            saksbehandlerident = null,
                            felt = BehandlingChangedEvent.Felt.AVSLUTTET_AV_SAKSBEHANDLER_TIDSPUNKT,
                            behandlingId = UUID.randomUUID(),
                        ),
                    ),
            )

        val ankeITrygderettenbehandlingChangedEvent =
            BehandlingChangedEvent(
                behandling = ankeITrygderettenbehandlingOMP,
                changeList =
                    listOf(
                        BehandlingChangedEvent.Change(
                            saksbehandlerident = null,
                            felt = BehandlingChangedEvent.Felt.AVSLUTTET_TIDSPUNKT,
                            behandlingId = UUID.randomUUID(),
                        ),
                    ),
            )

        assertTrue(statistikkTilDVHService.shouldSendStats(klagebehandlingChangedEvent))
        assertFalse(statistikkTilDVHService.shouldSendStats(klagebehandlingHJEEndretEvent))
        assertTrue(statistikkTilDVHService.shouldSendStats(ankebehandlingChangedEvent))
        assertFalse(statistikkTilDVHService.shouldSendStats(ankeITrygderettenbehandlingChangedEvent))
    }

    @Test
    fun `DVH mapping handles generell sak without fagsakId from IT01`() {
        val payload = processAndGetPayload(klagebehandling(fagsystem = Fagsystem.IT01, fagsakId = null, sakstype = Sakstype.GENERELL_SAK))

        assertThat(payload.get("behandlingId").asString()).isEqualTo("kildeReferanse")
        assertThat(payload.get("opprinneligFagsakId").isNull).isTrue()
    }

    @Test
    fun `DVH mapping formats Infotrygd fagsakId when present`() {
        val payload = processAndGetPayload(klagebehandling(fagsystem = Fagsystem.IT01, fagsakId = "12345678", sakstype = Sakstype.FAGSAK))

        assertThat(payload.get("behandlingId").asString()).isEqualTo("1234-5-678")
        assertThat(payload.get("opprinneligFagsakId").asString()).isEqualTo("12345678")
    }

    private fun processAndGetPayload(behandling: Behandling): JsonNode {
        val kafkaEventSlot = slot<KafkaEvent>()
        every { kafkaEventRepository.save(capture(kafkaEventSlot)) } answers { kafkaEventSlot.captured }

        statistikkTilDVHService.process(
            BehandlingChangedEvent(
                behandling = behandling,
                changeList =
                    listOf(
                        BehandlingChangedEvent.Change(
                            saksbehandlerident = null,
                            felt = BehandlingChangedEvent.Felt.KLAGEBEHANDLING_MOTTATT,
                            behandlingId = behandling.id,
                        ),
                    ),
            ),
        )

        return jacksonObjectMapper().readTree(kafkaEventSlot.captured.jsonPayload)
    }

    private fun klagebehandling(
        fagsystem: Fagsystem,
        fagsakId: String?,
        sakstype: Sakstype,
    ) = Klagebehandling(
        mottattVedtaksinstans = LocalDate.now(),
        previousSaksbehandlerident = null,
        avsenderEnhetFoersteinstans = "",
        kommentarFraFoersteinstans = null,
        klager = Klager(id = UUID.randomUUID(), partId = PartId(type = PartIdType.VIRKSOMHET, value = "889640782")),
        sakenGjelder = SakenGjelder(id = UUID.randomUUID(), partId = PartId(type = PartIdType.VIRKSOMHET, value = "889640782")),
        prosessfullmektig = null,
        ytelse = Ytelse.OMS_OMP,
        type = Type.KLAGE,
        kildeReferanse = "kildeReferanse",
        dvhReferanse = null,
        fagsystem = fagsystem,
        fagsakId = fagsakId,
        sakstype = sakstype,
        arkivsaksnummer = if (sakstype == Sakstype.GENERELL_SAK) "140411735" else null,
        mottattKlageinstans = LocalDateTime.now(),
        frist = LocalDate.now(),
        tildeling = null,
        tildelingHistorikk = mutableSetOf(),
        kakaKvalitetsvurderingId = UUID.randomUUID(),
        kakaKvalitetsvurderingVersion = 0,
        saksdokumenter = mutableSetOf(),
        hjemler = setOf(),
        sattPaaVent = null,
        gosysOppgaveId = null,
        varsletBehandlingstid = null,
        forlengetBehandlingstidDraft = null,
        gosysOppgaveRequired = false,
        initiatingSystem = Behandling.InitiatingSystem.KABAL,
        previousBehandlingId = null,
    )

    private val klagebehandlingOMP =
        Klagebehandling(
            mottattVedtaksinstans = LocalDate.now(),
            previousSaksbehandlerident = null,
            avsenderEnhetFoersteinstans = "",
            kommentarFraFoersteinstans = null,
            klager =
                Klager(
                    id = UUID.randomUUID(),
                    partId =
                        PartId(
                            type = PartIdType.PERSON,
                            value = "",
                        ),
                ),
            sakenGjelder =
                SakenGjelder(
                    id = UUID.randomUUID(),
                    partId =
                        PartId(
                            type = PartIdType.PERSON,
                            value = "",
                        ),
                ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.KLAGE,
            kildeReferanse = "",
            dvhReferanse = null,
            fagsystem = Fagsystem.K9,
            fagsakId = "",
            sakstype = Sakstype.FAGSAK,
            arkivsaksnummer = null,
            mottattKlageinstans = LocalDateTime.now(),
            frist = LocalDate.now(),
            tildeling = null,
            tildelingHistorikk = mutableSetOf(),
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 0,
            saksdokumenter = mutableSetOf(),
            hjemler = setOf(),
            sattPaaVent = null,
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

    private val klagebehandlingHJE =
        Klagebehandling(
            mottattVedtaksinstans = LocalDate.now(),
            previousSaksbehandlerident = null,
            avsenderEnhetFoersteinstans = "",
            kommentarFraFoersteinstans = null,
            klager =
                Klager(
                    id = UUID.randomUUID(),
                    partId =
                        PartId(
                            type = PartIdType.PERSON,
                            value = "",
                        ),
                ),
            sakenGjelder =
                SakenGjelder(
                    id = UUID.randomUUID(),
                    partId =
                        PartId(
                            type = PartIdType.PERSON,
                            value = "",
                        ),
                ),
            prosessfullmektig = null,
            ytelse = Ytelse.HJE_HJE,
            type = Type.KLAGE,
            kildeReferanse = "",
            dvhReferanse = null,
            fagsystem = Fagsystem.IT01,
            fagsakId = "",
            sakstype = Sakstype.FAGSAK,
            arkivsaksnummer = null,
            mottattKlageinstans = LocalDateTime.now(),
            frist = LocalDate.now(),
            tildeling = null,
            tildelingHistorikk = mutableSetOf(),
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 0,
            saksdokumenter = mutableSetOf(),
            hjemler = setOf(),
            sattPaaVent = null,
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = true,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

    private val ankebehandlingOMP =
        AnkebehandlingFoer2027(
            klager =
                Klager(
                    id = UUID.randomUUID(),
                    partId =
                        PartId(
                            type = PartIdType.PERSON,
                            value = "",
                        ),
                ),
            sakenGjelder =
                SakenGjelder(
                    id = UUID.randomUUID(),
                    partId =
                        PartId(
                            type = PartIdType.PERSON,
                            value = "",
                        ),
                ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.ANKE_FOER_2027,
            kildeReferanse = "",
            dvhReferanse = null,
            fagsystem = Fagsystem.K9,
            fagsakId = "",
            sakstype = Sakstype.FAGSAK,
            arkivsaksnummer = null,
            mottattKlageinstans = LocalDateTime.now(),
            frist = LocalDate.now(),
            tildeling = null,
            tildelingHistorikk = mutableSetOf(),
            kakaKvalitetsvurderingId = UUID.randomUUID(),
            kakaKvalitetsvurderingVersion = 0,
            saksdokumenter = mutableSetOf(),
            hjemler = setOf(),
            sattPaaVent = null,
            klageVedtaksDato = null,
            klageBehandlendeEnhet = "",
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            varsletBehandlingstid = null,
            forlengetBehandlingstidDraft = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )

    private val ankeITrygderettenbehandlingOMP =
        AnkeITrygderettenbehandlingFoer2027(
            klager =
                Klager(
                    id = UUID.randomUUID(),
                    partId =
                        PartId(
                            type = PartIdType.PERSON,
                            value = "",
                        ),
                ),
            sakenGjelder =
                SakenGjelder(
                    id = UUID.randomUUID(),
                    partId =
                        PartId(
                            type = PartIdType.PERSON,
                            value = "",
                        ),
                ),
            prosessfullmektig = null,
            ytelse = Ytelse.OMS_OMP,
            type = Type.ANKE_I_TRYGDERETTEN_FOER_2027,
            kildeReferanse = "",
            dvhReferanse = null,
            fagsystem = Fagsystem.K9,
            fagsakId = "",
            sakstype = Sakstype.FAGSAK,
            arkivsaksnummer = null,
            mottattKlageinstans = LocalDateTime.now(),
            frist = LocalDate.now(),
            tildeling = null,
            tildelingHistorikk = mutableSetOf(),
            saksdokumenter = mutableSetOf(),
            hjemler = setOf(),
            sattPaaVent = null,
            sendtTilTrygderetten = LocalDateTime.now(),
            kjennelseMottatt = null,
            previousSaksbehandlerident = "C78901",
            gosysOppgaveId = null,
            gosysOppgaveRequired = false,
            initiatingSystem = Behandling.InitiatingSystem.KABAL,
            previousBehandlingId = null,
        )
}
