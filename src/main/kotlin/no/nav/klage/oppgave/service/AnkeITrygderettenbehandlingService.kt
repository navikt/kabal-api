package no.nav.klage.oppgave.service

import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.ytelseToHjemler
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.view.OversendtAnkeITrygderettenFromArena
import no.nav.klage.oppgave.api.view.OversendtAnkeITrygderettenV1
import no.nav.klage.oppgave.api.view.createAnkeITrygderettenbehandlingFoer2027Input
import no.nav.klage.oppgave.api.view.toAnkeITrygderettenbehandlingFoer2027Input
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingEtter2027Input
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027
import no.nav.klage.oppgave.domain.behandling.AnkeITrygderettenbehandlingFoer2027Input
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent
import no.nav.klage.oppgave.domain.events.BehandlingChangedEvent.Change.Companion.createChange
import no.nav.klage.oppgave.domain.kafka.BehandlingState
import no.nav.klage.oppgave.domain.kafka.BehandlingType
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.KafkaEvent
import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.exceptions.InvalidProperty
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.exceptions.SectionedValidationErrorWithDetailsException
import no.nav.klage.oppgave.exceptions.ValidationSection
import no.nav.klage.oppgave.repositories.AnkeITrygderettenbehandlingEtter2027Repository
import no.nav.klage.oppgave.repositories.AnkeITrygderettenbehandlingFoer2027Repository
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
import java.util.UUID

@Service
@Transactional
class AnkeITrygderettenbehandlingService(
    private val ankeITrygderettenbehandlingFoer2027Repository: AnkeITrygderettenbehandlingFoer2027Repository,
    private val ankeITrygderettenbehandlingEtter2027Repository: AnkeITrygderettenbehandlingEtter2027Repository,
    private val behandlingService: BehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val kafkaEventRepository: KafkaEventRepository,
    @Value($$"${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val mottakService: MottakService,
    private val dokumentService: DokumentService,
    private val gosysOppgaveService: GosysOppgaveService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val kantService: KantService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val jacksonObjectMapper = jacksonObjectMapper()
    }

    fun createAnkeITrygderettenbehandlingFoer2027(input: AnkeITrygderettenbehandlingFoer2027Input): AnkeITrygderettenbehandlingFoer2027 {
        val ankeITrygderettenbehandlingFoer2027 =
            ankeITrygderettenbehandlingFoer2027Repository.save(
                AnkeITrygderettenbehandlingFoer2027(
                    klager = input.klager.copy(),
                    sakenGjelder = input.sakenGjelder?.copy() ?: input.klager.toSakenGjelder(),
                    prosessfullmektig = input.prosessfullmektig,
                    ytelse = input.ytelse,
                    type = input.type,
                    kildeReferanse = input.kildeReferanse,
                    dvhReferanse = input.dvhReferanse,
                    fagsystem = input.fagsystem,
                    fagsakId = input.fagsakId,
                    sakstype = input.sakstype,
                    arkivsaksnummer = input.arkivsaksnummer,
                    mottattKlageinstans = input.sakMottattKlageinstans,
                    tildeling = null,
                    hjemler =
                        if (input.innsendingsHjemler.isNullOrEmpty()) {
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
                ),
            )
        logger.debug("Created ankeITrygderettenbehandlingFoer2027 {}", ankeITrygderettenbehandlingFoer2027.id)

        if (kantService.kantShouldBeUsedForOldTypes()) {
            ankeITrygderettenbehandlingFoer2027.kantReference = kantService.createSaksdataInKant(ankeITrygderettenbehandlingFoer2027).id
        }

        behandlingService.washAndSetRegistreringshjemler(
            registreringsHjemmelSet = input.registreringsHjemmelSet,
            ytelse = input.ytelse,
            behandlingId = ankeITrygderettenbehandlingFoer2027.id,
        )

        if (input.saksdokumenter.isNotEmpty()) {
            behandlingService.connectDocumentsToBehandling(
                behandlingId = ankeITrygderettenbehandlingFoer2027.id,
                journalfoertDokumentReferenceSet =
                    input.saksdokumenter
                        .map {
                            JournalfoertDokumentReference(
                                journalpostId = it.journalpostId,
                                dokumentInfoId = it.dokumentInfoId,
                            )
                        }.toSet(),
                saksbehandlerIdent = systembrukerIdent,
                systemUserContext = true,
                ignoreCheckSkrivetilgang = true,
            )
        }

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankeITrygderettenbehandlingFoer2027,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = BehandlingChangedEvent.Felt.ANKE_I_TRYGDERETTEN_OPPRETTET,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankeITrygderettenbehandlingFoer2027.id,
                        ),
                    ),
            ),
        )

        return ankeITrygderettenbehandlingFoer2027
    }

    fun createAnkeITrygderettenbehandlingEtter2027(input: AnkeITrygderettenbehandlingEtter2027Input): AnkeITrygderettenbehandlingEtter2027 {
        val ankeITrygderettenbehandlingEtter2027 =
            ankeITrygderettenbehandlingEtter2027Repository.save(
                AnkeITrygderettenbehandlingEtter2027(
                    klager = input.klager.copy(),
                    sakenGjelder = input.sakenGjelder?.copy() ?: input.klager.toSakenGjelder(),
                    prosessfullmektig = input.prosessfullmektig,
                    ytelse = input.ytelse,
                    type = input.type,
                    kildeReferanse = input.kildeReferanse,
                    dvhReferanse = input.dvhReferanse,
                    fagsystem = input.fagsystem,
                    fagsakId = input.fagsakId,
                    sakstype = input.sakstype,
                    arkivsaksnummer = input.arkivsaksnummer,
                    mottattKlageinstans = input.sakMottattKlageinstans,
                    tildeling = null,
                    hjemler =
                        if (input.innsendingsHjemler.isNullOrEmpty()) {
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
                    trygderettenSaksnummer = input.trygderettenSaksnummer,
                ),
            )

        ankeITrygderettenbehandlingEtter2027.kantReference = kantService.createSaksdataInKant(ankeITrygderettenbehandlingEtter2027).id

        logger.debug("Created ankeITrygderettenbehandlingEtter2027 {}", ankeITrygderettenbehandlingEtter2027.id)

        behandlingService.washAndSetRegistreringshjemler(
            registreringsHjemmelSet = input.registreringsHjemmelSet,
            ytelse = input.ytelse,
            behandlingId = ankeITrygderettenbehandlingEtter2027.id,
        )

        if (input.saksdokumenter.isNotEmpty()) {
            behandlingService.connectDocumentsToBehandling(
                behandlingId = ankeITrygderettenbehandlingEtter2027.id,
                journalfoertDokumentReferenceSet =
                    input.saksdokumenter
                        .map {
                            JournalfoertDokumentReference(
                                journalpostId = it.journalpostId,
                                dokumentInfoId = it.dokumentInfoId,
                            )
                        }.toSet(),
                saksbehandlerIdent = systembrukerIdent,
                systemUserContext = true,
                ignoreCheckSkrivetilgang = true,
            )
        }

        applicationEventPublisher.publishEvent(
            BehandlingChangedEvent(
                behandling = ankeITrygderettenbehandlingEtter2027,
                changeList =
                    listOfNotNull(
                        createChange(
                            saksbehandlerident = systembrukerIdent,
                            felt = BehandlingChangedEvent.Felt.ANKE_I_TRYGDERETTEN_OPPRETTET,
                            fraVerdi = null,
                            tilVerdi = "Opprettet",
                            behandlingId = ankeITrygderettenbehandlingEtter2027.id,
                        ),
                    ),
            ),
        )

        return ankeITrygderettenbehandlingEtter2027
    }

    fun createAnkeITrygderettenbehandlingFromExternalApi(input: OversendtAnkeITrygderettenV1) {
        mottakService.validateAnkeITrygderettenV1(input)
        val inputDocuments =
            dokumentService.createSaksdokumenterFromJournalpostIdList(input.tilknyttedeJournalposter.map { it.journalpostId })
        val ankeITrygderettenbehandlingFoer2027 =
            createAnkeITrygderettenbehandlingFoer2027(
                input.createAnkeITrygderettenbehandlingFoer2027Input(inputDocuments),
            )

        // Custom handling for Pesys:
        if (ankeITrygderettenbehandlingFoer2027.fagsystem == Fagsystem.PP01) {
            val statistikkTilDVH =
                StatistikkTilDVH(
                    eventId = UUID.randomUUID(),
                    behandlingId = ankeITrygderettenbehandlingFoer2027.dvhReferanse,
                    behandlingIdKabal = ankeITrygderettenbehandlingFoer2027.toString(),
                    // Means enhetTildeltDato
                    behandlingStartetKA = null,
                    ansvarligEnhetKode = "TR0000",
                    behandlingStatus = BehandlingState.SENDT_TIL_TR,
                    behandlingType = BehandlingType.ANKE,
                    // Means medunderskriver
                    beslutter = null,
                    endringstid = ankeITrygderettenbehandlingFoer2027.sendtTilTrygderetten,
                    hjemmel = emptyList(),
                    klager =
                        getDVHPart(
                            type = ankeITrygderettenbehandlingFoer2027.klager.partId.type,
                            value = ankeITrygderettenbehandlingFoer2027.klager.partId.value,
                        ),
                    opprinneligFagsaksystem = ankeITrygderettenbehandlingFoer2027.fagsystem.navn,
                    overfoertKA = ankeITrygderettenbehandlingFoer2027.mottattKlageinstans.toLocalDate(),
                    resultat = null,
                    sakenGjelder =
                        getDVHPart(
                            type = ankeITrygderettenbehandlingFoer2027.sakenGjelder.partId.type,
                            value = ankeITrygderettenbehandlingFoer2027.sakenGjelder.partId.value,
                        ),
                    saksbehandler = ankeITrygderettenbehandlingFoer2027.tildeling?.saksbehandlerident,
                    saksbehandlerEnhet = ankeITrygderettenbehandlingFoer2027.tildeling?.enhet,
                    tekniskTid = LocalDateTime.now(),
                    vedtaksdato = null,
                    ytelseType = ankeITrygderettenbehandlingFoer2027.ytelse.name,
                    opprinneligFagsakId = ankeITrygderettenbehandlingFoer2027.fagsakId,
                )

            kafkaEventRepository.save(
                KafkaEvent(
                    id = UUID.randomUUID(),
                    behandlingId = ankeITrygderettenbehandlingFoer2027.id,
                    kilde = ankeITrygderettenbehandlingFoer2027.fagsystem.navn,
                    kildeReferanse = ankeITrygderettenbehandlingFoer2027.kildeReferanse,
                    status = UtsendingStatus.IKKE_SENDT,
                    jsonPayload = statistikkTilDVH.toJson(),
                    type = EventType.STATS_DVH,
                ),
            )
        }
    }

    fun createAnkeITrygderettenbehandlingFromArena(input: OversendtAnkeITrygderettenFromArena): UUID {
        if (!innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter()) {
            throw MissingTilgangException("Bare bruker med rollen `Oppgavestyring alle enheter` kan utføre denne operasjonen.")
        }

        mottakService.validateAnkeITrygderettenFromArena(input)
        input.validate()
        val newAnkeITrygderettenbehandling =
            createAnkeITrygderettenbehandlingFoer2027(
                input.toAnkeITrygderettenbehandlingFoer2027Input(),
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

    private fun OversendtAnkeITrygderettenFromArena.validate() {
        val validationErrors = mutableListOf<InvalidProperty>()
        val ytelse = Ytelse.of(ytelseId)
        if (hjemmelIdList.isEmpty()) {
            validationErrors.add(
                InvalidProperty(
                    field = "hjemmelIdList",
                    reason = "Behandling kan ikke registreres, mangler hjemmel.",
                ),
            )
        } else {
            hjemmelIdList.forEach { hjemmelId ->
                val hjemmel = Hjemmel.of(hjemmelId)
                if (ytelseToHjemler[ytelse]!!.filter { !it.utfases }.none { it.hjemmel == hjemmel }) {
                    validationErrors.add(
                        InvalidProperty(
                            field = "hjemmelIdList",
                            reason =
                                "Behandling med ytelse ${ytelse.navn} kan ikke registreres med hjemmel $hjemmel. Ta kontakt med team " +
                                    "klage dersom du mener hjemmelen skal være mulig å bruke for denne ytelsen.",
                        ),
                    )
                }
            }
        }

        val sakMottattKlageinstansParsed =
            try {
                LocalDate.parse(sakMottattKlageinstans)
            } catch (_: DateTimeParseException) {
                null
            }

        if (sakMottattKlageinstansParsed == null) {
            validationErrors.add(
                InvalidProperty(
                    field = "sakMottattKlageinstans",
                    reason = "Dato for anke mottatt klageinstans må være oppgitt på riktig form.",
                ),
            )
        } else {
            if (sakMottattKlageinstansParsed.isAfter(LocalDate.now())) {
                validationErrors.add(
                    InvalidProperty(
                        field = "sakMottattKlageinstans",
                        reason = "Dato for anke mottatt klageinstans kan ikke være i fremtiden.",
                    ),
                )
            }
        }

        val sendtTilTrygderettenParsed =
            try {
                LocalDate.parse(sendtTilTrygderetten)
            } catch (_: DateTimeParseException) {
                null
            }

        if (sendtTilTrygderettenParsed == null) {
            validationErrors.add(
                InvalidProperty(
                    field = "sendtTilTrygderetten",
                    reason = "Dato for anke sendt til Trygderetten må være oppgitt på riktig form.",
                ),
            )
        } else {
            if (sendtTilTrygderettenParsed.isAfter(LocalDate.now())) {
                validationErrors.add(
                    InvalidProperty(
                        field = "sendtTilTrygderetten",
                        reason = "Dato for anke sendt til Trygderetten kan ikke være i fremtiden.",
                    ),
                )
            }
        }

        if (fagsakId.isEmpty() || !fagsakId.isNumeric()) {
            validationErrors.add(
                InvalidProperty(
                    field = "fagsakId",
                    reason = "Arkivsaksnummer må bestå av bare tall og være et heltall.",
                ),
            )
        }

        if (gosysOppgaveId <= 0) {
            validationErrors.add(
                InvalidProperty(
                    field = "gosysOppgaveId",
                    reason = "Gosysoppgave må være valgt.",
                ),
            )
        } else {
            if (behandlingService.findOpenBehandlingUsingGosysOppgave(gosysOppgaveId) != null) {
                validationErrors.add(
                    InvalidProperty(
                        field = "gosysOppgaveId",
                        reason = "Gosysoppgave med id $gosysOppgaveId er allerede i bruk i annen behandling.",
                    ),
                )
            }
        }

        if (validationErrors.isNotEmpty()) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Validation error",
                sections =
                    listOf(
                        ValidationSection(
                            section = "behandling",
                            properties = validationErrors,
                        ),
                    ),
            )
        }
    }

    fun String.isNumeric(): Boolean = this.matches("^[0-9]*$".toRegex())
}
