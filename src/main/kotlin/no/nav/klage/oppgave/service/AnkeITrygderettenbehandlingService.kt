package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.ytelseToHjemler
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.view.OversendtAnkeITrygderettenFraArena
import no.nav.klage.oppgave.api.view.OversendtAnkeITrygderettenV1
import no.nav.klage.oppgave.api.view.createAnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.api.view.toAnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandling
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingInput
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.exceptions.InvalidProperty
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.exceptions.SectionedValidationErrorWithDetailsException
import no.nav.klage.oppgave.exceptions.ValidationSection
import no.nav.klage.oppgave.repositories.AnkeITrygderettenbehandlingRepository
import no.nav.klage.oppgave.repositories.KafkaEventRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
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
    private val gosysOppgaveService: GosysOppgaveService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val jacksonObjectMapper = jacksonObjectMapper()
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
                paaanketVedtaksdato = input.paaanketVedtaksdato,
                forsterketRett = input.forsterketRett,
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

        if (input.saksdokumenter.isNotEmpty()) {
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
        }

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
                    jsonPayload = jacksonObjectMapper.writeValueAsString(behandlingEvent),
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

    fun createAnkeITrygderettenbehandlingFromArenaExternalApi(input: OversendtAnkeITrygderettenFraArena): UUID {
        if (!innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter()) {
            throw MissingTilgangException("Bare bruker med rollen `Oppgavestyring alle enheter` kan utføre denne operasjonen.")
        }

        mottakService.validateAnkeITrygderettenFraArena(input)
        input.validate()
        val newAnkeITrygderettenbehandling = createAnkeITrygderettenbehandling(
            input.toAnkeITrygderettenbehandlingInput()
        )
        gosysOppgaveService.addKommentar(
            behandling = newAnkeITrygderettenbehandling,
            kommentar = "Anke i Trygderetten overført til Kabal",
            systemContext = false,
            throwExceptionIfFerdigstilt = true,
        )

        return newAnkeITrygderettenbehandling.id
    }

    private fun StatistikkTilDVH.toJson(): String = jacksonObjectMapper.writeValueAsString(this)

    private fun OversendtAnkeITrygderettenFraArena.validate() {
        val validationErrors = mutableListOf<InvalidProperty>()
        val ytelse = Ytelse.of(ytelseId)
        if (hjemmelIdList.isEmpty()) {
            validationErrors.add(
                InvalidProperty(
                    field = "hjemmelIdList",
                    reason = "Behandling kan ikke registreres, mangler hjemmel."
                )
            )
        } else {
            hjemmelIdList.forEach { hjemmelId ->
                val hjemmel = Hjemmel.of(hjemmelId)
                if (!ytelseToHjemler[ytelse]!!.filter { !it.utfases }.any { it.hjemmel == hjemmel }) {
                    validationErrors.add(
                        InvalidProperty(
                            field = "hjemmelIdList",
                            reason = "Behandling med ytelse ${ytelse.navn} kan ikke registreres med hjemmel $hjemmel. Ta kontakt med team klage dersom du mener hjemmelen skal være mulig å bruke for denne ytelsen."
                        )
                    )
                }
            }
        }

        val sakMottattKlageinstansParsed = try {
            LocalDate.parse(sakMottattKlageinstans)
        } catch (_: DateTimeParseException) {
            null
        }

        if (sakMottattKlageinstansParsed == null) {
            validationErrors.add(
                InvalidProperty(
                    field = "sakMottattKlageinstans",
                    reason = "Dato for anke mottatt klageinstans må være oppgitt."
                )
            )
        } else {
            if (LocalDate.now().isBefore(sakMottattKlageinstansParsed)) {
                validationErrors.add(
                    InvalidProperty(
                        field = "sakMottattKlageinstans",
                        reason = "Dato for anke mottatt klageinstans kan ikke være i fremtiden."
                    )
                )
            }
        }

        val sendtTilTrygderettenParsed = try {
            LocalDate.parse(sendtTilTrygderetten)
        } catch (_: DateTimeParseException) {
            null
        }

        if (sendtTilTrygderettenParsed == null) {
            validationErrors.add(
                InvalidProperty(
                    field = "sendtTilTrygderetten",
                    reason = "Dato for anke sendt til Trygderetten må være oppgitt."
                )
            )
        } else {
            if (LocalDate.now().isBefore(sendtTilTrygderettenParsed)) {
                validationErrors.add(
                    InvalidProperty(
                        field = "sendtTilTrygderetten",
                        reason = "Dato for anke sendt til Trygderetten kan ikke være i fremtiden."
                    )
                )
            }
        }

        if (gosysOppgaveId <= 0) {
            validationErrors.add(
                InvalidProperty(
                    field = "gosysOppgaveId",
                    reason = "GosysOppgaveId må være et positivt heltall."
                )
            )
        }

        if (validationErrors.isNotEmpty()) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Validation error",
                sections = listOf(
                    ValidationSection(
                        section = "behandling",
                        properties = validationErrors
                    )
                )
            )
        }
    }
}