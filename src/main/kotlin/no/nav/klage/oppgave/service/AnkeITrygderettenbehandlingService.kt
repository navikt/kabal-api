package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.api.view.OversendtAnkeITrygderettenV1
import no.nav.klage.oppgave.api.view.createAnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.repositories.AnkeITrygderettenbehandlingRepository
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.ourJsonMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class AnkeITrygderettenbehandlingService(
    private val ankeITrygderettenbehandlingRepository: AnkeITrygderettenbehandlingRepository,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val kafkaEventRepository: KafkaEventRepository,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val mottakService: MottakService,
    private val dokumentService: DokumentService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val objectMapper = ourJsonMapper()
        private val objectMapperBehandlingEvents =
            JsonMapper.builder().build()
    }

    fun createAnkeITrygderettenbehandling(input: AnkeITrygderettenbehandlingInput): AnkeITrygderettenbehandling {
        val ankeITrygderettenbehandling = ankeITrygderettenbehandlingRepository.save(
            AnkeITrygderettenbehandling(
                klager = input.klager.copy(),
                sakenGjelder = input.sakenGjelder?.copy() ?: input.klager.toSakenGjelder(),
                prosessfullmektig = input.prosessfullmektig,
                ytelse = input.ytelse,
                type = input.type,
                kildeReferanse = input.kildeReferanse,
                dvhReferanse = input.dvhReferanse,
                fagsystem = input.fagsystem,
                fagsakId = input.fagsakId,
                mottattKlageinstans = input.sakMottattKlageinstans,
                tildeling = null,
                hjemler = if (input.innsendingsHjemler.isNullOrEmpty()) {
                    mutableSetOf(Hjemmel.MANGLER)
                } else {
                    input.innsendingsHjemler
                },
                sendtTilTrygderetten = input.sendtTilTrygderetten,
                kjennelseMottatt = null,
                previousSaksbehandlerident = input.previousSaksbehandlerident,
                gosysOppgaveId = input.gosysOppgaveId,
                tilbakekreving = input.tilbakekreving,
                gosysOppgaveRequired = input.gosysOppgaveRequired,
                initiatingSystem = input.initiatingSystem,
                previousBehandlingId = input.previousBehandlingId,
            )
        )
        logger.debug("Created ankeITrygderettenbehandling {}", ankeITrygderettenbehandling.id)

        behandlingService.washAndSetRegistreringshjemler(
            registreringsHjemmelSet = input.registreringsHjemmelSet,
            ytelse = input.ytelse,
            behandlingId = ankeITrygderettenbehandling.id
        )

        behandlingService.connectDocumentsToBehandling(
            behandlingId = ankeITrygderettenbehandling.id,
            journalfoertDokumentReferenceSet = input.saksdokumenter.map {
                JournalfoertDokumentReference(
                    journalpostId = it.journalpostId,
                    dokumentInfoId = it.dokumentInfoId
                )
            }.toSet(),
            saksbehandlerIdent = systembrukerIdent,
            systemUserContext = true,
            ignoreCheckSkrivetilgang = true,
        )

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankeITrygderettenbehandling,
                changeList = listOfNotNull(
                    createChange(
                        saksbehandlerident = systembrukerIdent,
                        felt = BehandlingChangedEvent.Felt.ANKE_I_TRYGDERETTEN_OPPRETTET,
                        fraVerdi = null,
                        tilVerdi = "Opprettet",
                        behandlingId = ankeITrygderettenbehandling.id,
                    )
                )
            )
        )

        if (!ankeITrygderettenbehandling.gosysOppgaveRequired) {
            //Publiser Kafka-event, infomelding om opprettelse
            val behandlingEvent = BehandlingEvent(
                eventId = UUID.randomUUID(),
                kildeReferanse = ankeITrygderettenbehandling.kildeReferanse,
                kilde = ankeITrygderettenbehandling.fagsystem.navn,
                kabalReferanse = ankeITrygderettenbehandling.id.toString(),
                type = BehandlingEventType.ANKE_I_TRYGDERETTENBEHANDLING_OPPRETTET,
                detaljer = BehandlingDetaljer(
                    ankeITrygderettenbehandlingOpprettet =
                    AnkeITrygderettenbehandlingOpprettetDetaljer(
                        sendtTilTrygderetten = ankeITrygderettenbehandling.sendtTilTrygderetten,
                        utfall = input.ankebehandlingUtfall,
                    )
                )
            )

            kafkaEventRepository.save(
                KafkaEvent(
                    id = UUID.randomUUID(),
                    behandlingId = ankeITrygderettenbehandling.id,
                    kilde = ankeITrygderettenbehandling.fagsystem.navn,
                    kildeReferanse = ankeITrygderettenbehandling.kildeReferanse,
                    jsonPayload = objectMapperBehandlingEvents.writeValueAsString(behandlingEvent),
                    type = EventType.BEHANDLING_EVENT
                )
            )
        }

        return ankeITrygderettenbehandling
    }

    fun createAnkeITrygderettenbehandlingFromExternalApi(input: OversendtAnkeITrygderettenV1) {
        mottakService.validateAnkeITrygderettenV1(input)
        val inputDocuments =
            dokumentService.createSaksdokumenterFromJournalpostIdList(input.tilknyttedeJournalposter.map { it.journalpostId })
        val ankeITrygderettenbehandling = createAnkeITrygderettenbehandling(
            input.createAnkeITrygderettenbehandlingInput(inputDocuments)
        )

        //Custom handling for Pesys:
        if (ankeITrygderettenbehandling.fagsystem == Fagsystem.PP01) {
            val statistikkTilDVH = StatistikkTilDVH(
                eventId = UUID.randomUUID(),
                behandlingId = ankeITrygderettenbehandling.dvhReferanse,
                behandlingIdKabal = ankeITrygderettenbehandling.toString(),
                //Means enhetTildeltDato
                behandlingStartetKA = null,
                ansvarligEnhetKode = "TR0000",
                behandlingStatus = BehandlingState.SENDT_TIL_TR,
                behandlingType = Type.ANKE.name,
                //Means medunderskriver
                beslutter = null,
                endringstid = ankeITrygderettenbehandling.sendtTilTrygderetten,
                hjemmel = emptyList(),
                klager = getDVHPart(
                    type = ankeITrygderettenbehandling.klager.partId.type,
                    value = ankeITrygderettenbehandling.klager.partId.value
                ),
                opprinneligFagsaksystem = ankeITrygderettenbehandling.fagsystem.navn,
                overfoertKA = ankeITrygderettenbehandling.mottattKlageinstans.toLocalDate(),
                resultat = null,
                sakenGjelder = getDVHPart(
                    type = ankeITrygderettenbehandling.sakenGjelder.partId.type,
                    value = ankeITrygderettenbehandling.sakenGjelder.partId.value
                ),
                saksbehandler = ankeITrygderettenbehandling.tildeling?.saksbehandlerident,
                saksbehandlerEnhet = ankeITrygderettenbehandling.tildeling?.enhet,
                tekniskTid = LocalDateTime.now(),
                vedtaksdato = null,
                ytelseType = ankeITrygderettenbehandling.ytelse.name,
                opprinneligFagsakId = ankeITrygderettenbehandling.fagsakId,
            )

            kafkaEventRepository.save(
                KafkaEvent(
                    id = UUID.randomUUID(),
                    behandlingId = ankeITrygderettenbehandling.id,
                    kilde = ankeITrygderettenbehandling.fagsystem.navn,
                    kildeReferanse = ankeITrygderettenbehandling.kildeReferanse,
                    status = UtsendingStatus.IKKE_SENDT,
                    jsonPayload = statistikkTilDVH.toJson(),
                    type = EventType.STATS_DVH
                )
            )
        }
    }

    private fun StatistikkTilDVH.toJson(): String = objectMapper.writeValueAsString(this)
}