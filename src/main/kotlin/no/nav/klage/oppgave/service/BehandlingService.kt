package no.nav.klage.oppgave.service

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.klage.dokument.api.view.JournalfoertDokumentReference
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Adresse
import no.nav.klage.dokument.exceptions.DokumentValidationException
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.api.view.kabin.CompletedBehandling
import no.nav.klage.oppgave.api.view.kabin.toKabinPartView
import no.nav.klage.oppgave.clients.arbeidoginntekt.ArbeidOgInntektClient
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Medunderskrivere
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Saksbehandlere
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.HandledInKabalInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakAssignedInput
import no.nav.klage.oppgave.clients.saf.SafFacade
import no.nav.klage.oppgave.clients.saf.graphql.Journalstatus
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.kafka.*
import no.nav.klage.oppgave.domain.kafka.FullmektigEvent
import no.nav.klage.oppgave.domain.kafka.KlagerEvent
import no.nav.klage.oppgave.domain.kafka.MedunderskriverEvent
import no.nav.klage.oppgave.domain.kafka.Part
import no.nav.klage.oppgave.domain.kafka.RolEvent
import no.nav.klage.oppgave.domain.kafka.SattPaaVentEvent
import no.nav.klage.oppgave.domain.kafka.TildelingEvent
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandlingSetters.setKjennelseMottatt
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandlingSetters.setNyAnkebehandlingKA
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandlingSetters.setNyBehandlingEtterTROpphevet
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandlingSetters.setSendtTilTrygderetten
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.addSaksdokumenter
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.clearSaksdokumenter
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.removeSaksdokument
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setAvsluttetAvSaksbehandler
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setExtraUtfallSet
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setFeilregistrering
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setFrist
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setFullmektig
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setGosysOppgaveId
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setGosysOppgaveUpdate
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setIgnoreGosysOppgave
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setInnsendingshjemler
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setKlager
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setMedunderskriverFlowState
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setMedunderskriverNavIdent
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setMottattKlageinstans
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setROLFlowState
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setROLIdent
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setROLReturnedDate
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setRegistreringshjemler
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setSattPaaVent
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setTilbakekreving
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setTildeling
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setUtfall
import no.nav.klage.oppgave.domain.klage.KlagebehandlingSetters.setMottattVedtaksinstans
import no.nav.klage.oppgave.exceptions.*
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Service
@Transactional
class BehandlingService(
    private val behandlingRepository: BehandlingRepository,
    private val tilgangService: TilgangService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val kakaApiGateway: KakaApiGateway,
    private val dokumentService: DokumentService,
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val kabalInnstillingerService: KabalInnstillingerService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val arbeidOgInntektClient: ArbeidOgInntektClient,
    private val fssProxyClient: KlageFssProxyClient,
    private val eregClient: EregClient,
    private val saksbehandlerService: SaksbehandlerService,
    private val behandlingMapper: BehandlingMapper,
    private val historyService: HistoryService,
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val partSearchService: PartSearchService,
    private val safFacade: SafFacade,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val tokenUtil: TokenUtil,
    private val gosysOppgaveService: GosysOppgaveService,
    private val kodeverkService: KodeverkService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        private val objectMapper: ObjectMapper = ourJacksonObjectMapper()
    }

    fun ferdigstillBehandling(
        behandlingId: UUID,
        innloggetIdent: String,
        gosysOppgaveInput: GosysOppgaveInput?,
        nyBehandlingEtterTROpphevet: Boolean,
    ): BehandlingFullfoertView {
        if (gosysOppgaveInput?.gosysOppgaveUpdate != null && gosysOppgaveInput.ignoreGosysOppgave == true) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Validation error",
                sections = listOf(
                    ValidationSection(
                        section = "behandling",
                        properties = listOf(
                            InvalidProperty(
                                field = "gosysOppgaveInput",
                                reason = "Kan ikke både oppdatere Gosys-oppgaven og ignorere Gosys-oppgaven."
                            )
                        )
                    )
                )
            )
        }

        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId
        )

        val ankeITRHenvist = behandling is AnkeITrygderettenbehandling && behandling.utfall == Utfall.HENVIST

        if ((ankeITRHenvist || nyBehandlingEtterTROpphevet) && gosysOppgaveInput != null) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Validation error",
                sections = listOf(
                    ValidationSection(
                        section = "behandling",
                        properties = listOf(
                            InvalidProperty(
                                field = "gosysOppgaveUpdate",
                                reason = "Gosys-oppgaven kan ikke oppdateres for en ankebehandling som er henvist eller opphevet fra Trygderetten, og der det skal opprettes ny behandling i KA."
                            )
                        )
                    )
                )
            )
        }

        val omgjoeringskravWithUtfallThatLeadsToAutomaticFerdigstilling = behandling is Omgjoeringskravbehandling &&
                behandling.utfall in listOf(Utfall.STADFESTET_ANNEN_BEGRUNNELSE, Utfall.BESLUTNING_IKKE_OMGJOERE)

        if (omgjoeringskravWithUtfallThatLeadsToAutomaticFerdigstilling && gosysOppgaveInput != null) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Validation error",
                sections = listOf(
                    ValidationSection(
                        section = "behandling",
                        properties = listOf(
                            InvalidProperty(
                                field = "gosysOppgaveUpdate",
                                reason = "Gosys-oppgaven skal ikke oppdateres da den automatisk vil bli avsluttet i Kabal ved fullføring."
                            )
                        )
                    )
                )
            )
        }

        if (behandling.ferdigstilling != null) throw BehandlingFinalizedException("Behandlingen er avsluttet")

        //Forretningsmessige krav før vedtak kan ferdigstilles
        validateBehandlingBeforeFinalize(
            behandlingId = behandlingId,
            nyBehandlingEtterTROpphevet = nyBehandlingEtterTROpphevet
        )

        if (nyBehandlingEtterTROpphevet) {
            return behandlingMapper.mapToBehandlingFullfoertView(
                setNyBehandlingEtterTROpphevetAndSetToAvsluttet(
                    behandlingId,
                    innloggetIdent
                )
            )
        }

        validateAndUpdateGosysOppgaveInput(
            behandling = behandling,
            ankeITRHenvist = ankeITRHenvist,
            omgjoeringskravWithUtfallThatLeadsToAutomaticFerdigstilling = omgjoeringskravWithUtfallThatLeadsToAutomaticFerdigstilling,
            gosysOppgaveInput = gosysOppgaveInput,
            innloggetIdent = innloggetIdent
        )

        //Her settes en markør som så brukes async i kallet klagebehandlingRepository.findByAvsluttetIsNullAndAvsluttetAvSaksbehandlerIsNotNull
        return behandlingMapper.mapToBehandlingFullfoertView(
            markerBehandlingSomAvsluttetAvSaksbehandler(
                behandling,
                innloggetIdent
            )
        )
    }

    private fun validateAndUpdateGosysOppgaveInput(
        behandling: Behandling,
        ankeITRHenvist: Boolean,
        omgjoeringskravWithUtfallThatLeadsToAutomaticFerdigstilling: Boolean,
        gosysOppgaveInput: GosysOppgaveInput?,
        innloggetIdent: String
    ) {
        if (behandling.gosysOppgaveId != null) {
            if (ankeITRHenvist || omgjoeringskravWithUtfallThatLeadsToAutomaticFerdigstilling) {
                //Ikke relevant å håndtere Gosys-oppgave her
                logger.debug("Not updating Gosys oppgave, not relevant for this case. ankeITRHenvist: $ankeITRHenvist, omgjoeringskravWithUtfallThatLeadsToAutomaticFerdigstilling: $omgjoeringskravWithUtfallThatLeadsToAutomaticFerdigstilling")
                return
            } else {
                val gosysOppgave = gosysOppgaveService.getGosysOppgave(behandling.gosysOppgaveId!!)

                if (!gosysOppgave.editable && gosysOppgaveInput?.ignoreGosysOppgave != true) {
                    throw SectionedValidationErrorWithDetailsException(
                        title = "Validation error",
                        sections = listOf(
                            ValidationSection(
                                section = "behandling",
                                properties = listOf(
                                    InvalidProperty(
                                        field = "gosysOppgaveInput",
                                        reason = "Gosys-oppgaven kan ikke redigeres. Du må bekrefte at du fremdeles vil bruke denne Gosys-oppgaven, eller velge en annen."
                                    )
                                )
                            )
                        )
                    )
                }

                if (gosysOppgaveInput?.gosysOppgaveUpdate == null && gosysOppgaveInput?.ignoreGosysOppgave != true) {
                    throw SectionedValidationErrorWithDetailsException(
                        title = "Validation error",
                        sections = listOf(
                            ValidationSection(
                                section = "behandling",
                                properties = listOf(
                                    InvalidProperty(
                                        field = "gosysOppgaveUpdate",
                                        reason = "Oppdatert informasjon om Gosys-oppgaven må fylles ut for å avslutte behandlingen."
                                    )
                                )
                            )
                        )
                    )
                } else {
                    if (gosysOppgaveInput.gosysOppgaveUpdate != null) {
                        logger.debug("Updating behandling with gosysOppgaveInput")
                        behandling.setGosysOppgaveUpdate(
                            tildeltEnhet = gosysOppgaveInput.gosysOppgaveUpdate.tildeltEnhet,
                            mappeId = gosysOppgaveInput.gosysOppgaveUpdate.mappeId,
                            kommentar = gosysOppgaveInput.gosysOppgaveUpdate.kommentar,
                            saksbehandlerident = innloggetIdent,
                        )
                    } else {
                        logger.debug("Updating behandling with ignoreGosysOppgave")
                        //Her må ignoreGosysOppgave være true
                        behandling.setIgnoreGosysOppgave(
                            ignoreGosysOppgaveNewValue = true,
                            saksbehandlerident = innloggetIdent,
                        )
                    }
                }
            }
        }
    }

    private fun markerBehandlingSomAvsluttetAvSaksbehandler(
        behandling: Behandling,
        innloggetIdent: String
    ): Behandling {
        val event = behandling.setAvsluttetAvSaksbehandler(
            saksbehandlerident = innloggetIdent,
            saksbehandlernavn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent)
        )
        applicationEventPublisher.publishEvent(event)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                BehandlingFerdigstiltEvent(
                    actor = Employee(
                        navIdent = innloggetIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(innloggetIdent),
                    ),
                    timestamp = behandling.modified,
                    avsluttetAvSaksbehandlerDate = behandling.ferdigstilling!!.avsluttetAvSaksbehandler,
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.FERDIGSTILT,
        )

        return behandling
    }

    fun validateBehandlingBeforeFinalize(behandlingId: UUID, nyBehandlingEtterTROpphevet: Boolean) {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        val dokumentValidationErrors = mutableListOf<InvalidProperty>()
        val behandlingValidationErrors = mutableListOf<InvalidProperty>()
        val sectionList = mutableListOf<ValidationSection>()

        if (nyBehandlingEtterTROpphevet) {
            if (behandling is AnkeITrygderettenbehandling) {
                if (behandling.utfall != Utfall.OPPHEVET) {
                    throw IllegalOperation("Ny ankebehandling kan kun opprettes hvis utfall er 'Opphevet'.")
                }
            } else {
                throw IllegalOperation("Ny ankebehandling kan kun brukes på en Anke i trygderetten-sak.")
            }
        }

        val unfinishedDocuments =
            dokumentUnderArbeidRepository.findByBehandlingIdAndMarkertFerdigIsNull(behandling.id)

        if (unfinishedDocuments.isNotEmpty()) {
            dokumentValidationErrors.add(
                InvalidProperty(
                    field = "underArbeid",
                    reason = "Ferdigstill eller slett alle dokumenter under arbeid."
                )
            )
        }

        if (dokumentValidationErrors.isNotEmpty()) {
            sectionList.add(
                ValidationSection(
                    section = "dokumenter",
                    properties = dokumentValidationErrors
                )
            )
        }

        if (behandling.utfall == null) {
            behandlingValidationErrors.add(
                InvalidProperty(
                    field = "utfall",
                    reason = "Sett et utfall på saken."
                )
            )
        }

        //TODO: Create test for invalid utfall when such are added
        if (behandling.utfall != null && behandling.utfall !in typeToUtfall[behandling.type]!!) {
            behandlingValidationErrors.add(
                InvalidProperty(
                    field = "utfall",
                    reason = "Dette utfallet er ikke gyldig for denne behandlingstypen."
                )
            )
        }

        if (behandling.utfall !in noRegistringshjemmelNeeded) {
            if (behandling.registreringshjemler.isEmpty()) {
                behandlingValidationErrors.add(
                    InvalidProperty(
                        field = "hjemmel",
                        reason = "Sett en eller flere hjemler på saken."
                    )
                )
            }
        }

        if (behandling.type !in listOf(
                Type.ANKE_I_TRYGDERETTEN,
                Type.BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET,
                Type.OMGJOERINGSKRAV,
            ) && behandling.utfall !in noKvalitetsvurderingNeeded
        ) {
            val kvalitetsvurderingValidationErrors = kakaApiGateway.getValidationErrors(behandling)

            if (kvalitetsvurderingValidationErrors.isNotEmpty()) {
                sectionList.add(
                    ValidationSection(
                        section = "kvalitetsvurdering",
                        properties = kvalitetsvurderingValidationErrors
                    )
                )
            }
        }

        if (LocalDateTime.now().isBefore(behandling.mottattKlageinstans)) {
            behandlingValidationErrors.add(
                InvalidProperty(
                    field = "mottattKlageinstans",
                    reason = "Denne datoen kan ikke være i fremtiden."
                )
            )
        }

        if (behandling is Klagebehandling &&
            LocalDate.now().isBefore(behandling.mottattVedtaksinstans)
        ) {
            behandlingValidationErrors.add(
                InvalidProperty(
                    field = "mottattVedtaksinstans",
                    reason = "Denne datoen kan ikke være i fremtiden."
                )
            )
        }

        if (behandling is AnkeITrygderettenbehandling) {
            if (behandling.kjennelseMottatt == null) {
                behandlingValidationErrors.add(
                    InvalidProperty(
                        field = "kjennelseMottatt",
                        reason = "Denne datoen må være satt."
                    )
                )
            }

            if (behandling.kjennelseMottatt != null
                && behandling.sendtTilTrygderetten.isAfter(behandling.kjennelseMottatt)
            ) {
                behandlingValidationErrors.add(
                    InvalidProperty(
                        field = "sendtTilTrygderetten",
                        reason = "Sendt til Trygderetten må være før Kjennelse mottatt."
                    )
                )
            }
        }

        if (behandling.prosessfullmektig?.partId != null) {
            if (behandling.prosessfullmektig?.partId?.isPerson() == false &&
                !eregClient.hentNoekkelInformasjonOmOrganisasjon(behandling.prosessfullmektig!!.partId!!.value)
                    .isActive()
            ) {
                behandlingValidationErrors.add(
                    InvalidProperty(
                        field = "fullmektig",
                        reason = "Fullmektig/organisasjon har opphørt."
                    )
                )
            }
        }

        if ((!behandling.fagsystem.modernized || behandling is OmgjoeringskravbehandlingBasedOnJournalpost) && behandling.gosysOppgaveId == null) {
            behandlingValidationErrors.add(
                InvalidProperty(
                    field = "gosysOppgave",
                    reason = "Velg Gosys-oppgave."
                )
            )
        }

        if (behandlingValidationErrors.isNotEmpty()) {
            sectionList.add(
                ValidationSection(
                    section = "behandling",
                    properties = behandlingValidationErrors
                )
            )
        }

        if (sectionList.isNotEmpty()) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Validation error",
                sections = sectionList
            )
        }
    }

    fun validateAnkeITrygderettenbehandlingBeforeNyAnkebehandling(behandlingId: UUID) {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId) as AnkeITrygderettenbehandling
        val dokumentValidationErrors = mutableListOf<InvalidProperty>()
        val behandlingValidationErrors = mutableListOf<InvalidProperty>()
        val sectionList = mutableListOf<ValidationSection>()

        val unfinishedDocuments =
            dokumentUnderArbeidRepository.findByBehandlingIdAndMarkertFerdigIsNull(behandling.id)

        if (unfinishedDocuments.isNotEmpty()) {
            dokumentValidationErrors.add(
                InvalidProperty(
                    field = "underArbeid",
                    reason = "Kan ikke lukke behandling. Ferdigstill eller slett alle dokumenter under arbeid."
                )
            )
        }

        if (dokumentValidationErrors.isNotEmpty()) {
            sectionList.add(
                ValidationSection(
                    section = "dokumenter",
                    properties = dokumentValidationErrors
                )
            )
        }

        fun getErrorText(prop: String) =
            "Kan ikke lukke behandling. Fjern $prop. Dersom Trygderetten har behandlet saken, kan du ikke starte ny behandling av samme sak."

        if (behandling.utfall != null) {
            when (behandling.utfall) {
                Utfall.HENVIST -> {
                    behandlingValidationErrors.add(
                        InvalidProperty(
                            field = "utfall",
                            reason = "Kan ikke lukke behandling. Dersom resultatet fra Trygderetten er «Henvist», må du først fullføre registrering av resultatet fra Trygderetten før du kan starte ny behandling. Når du trykker «Fullfør», vil Kabal opprette en ny ankeoppgave for deg."
                        )
                    )
                }

                Utfall.OPPHEVET -> {
                    behandlingValidationErrors.add(
                        InvalidProperty(
                            field = "utfall",
                            reason = "Kan ikke lukke behandling. Dersom resultatet fra Trygderetten er «Opphevet», må du først fullføre registrering av resultatet fra Trygderetten før du kan starte ny behandling. Når du trykker «Fullfør», vil du få mulighet til å opprette en ny ankeoppgave."
                        )
                    )
                }

                else -> {
                    behandlingValidationErrors.add(
                        InvalidProperty(
                            field = "utfall",
                            reason = getErrorText("utfall")
                        )
                    )
                }
            }
        }

        if (behandling.registreringshjemler.isNotEmpty()) {
            behandlingValidationErrors.add(
                InvalidProperty(
                    field = "hjemmel",
                    reason = getErrorText("hjemler")
                )
            )
        }

        if (behandling.kjennelseMottatt != null) {
            behandlingValidationErrors.add(
                InvalidProperty(
                    field = "kjennelseMottatt",
                    reason = getErrorText("kjennelse mottatt")
                )
            )
        }

        if (behandlingValidationErrors.isNotEmpty()) {
            sectionList.add(
                ValidationSection(
                    section = "behandling",
                    properties = behandlingValidationErrors
                )
            )
        }

        if (sectionList.isNotEmpty()) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Validation error",
                sections = sectionList
            )
        }
    }

    fun validateFeilregistrering(behandlingId: UUID) {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        val dokumentValidationErrors = mutableListOf<InvalidProperty>()
        val behandlingValidationErrors = mutableListOf<InvalidProperty>()
        val sectionList = mutableListOf<ValidationSection>()

        val unfinishedDocuments =
            dokumentUnderArbeidRepository.findByBehandlingIdAndMarkertFerdigIsNull(behandling.id)

        if (unfinishedDocuments.isNotEmpty()) {
            dokumentValidationErrors.add(
                InvalidProperty(
                    field = "underArbeid",
                    reason = "Ferdigstill eller slett alle dokumenter under arbeid."
                )
            )
        }

        if (dokumentValidationErrors.isNotEmpty()) {
            sectionList.add(
                ValidationSection(
                    section = "dokumenter",
                    properties = dokumentValidationErrors
                )
            )
        }

        //TODO: Denne er alltid tom nå. Burde vi sjekke noe annet her?
        if (behandlingValidationErrors.isNotEmpty()) {
            sectionList.add(
                ValidationSection(
                    section = "behandling",
                    properties = behandlingValidationErrors
                )
            )
        }

        if (sectionList.isNotEmpty()) {
            throw SectionedValidationErrorWithDetailsException(
                title = "Validation error",
                sections = sectionList
            )
        }
    }

    fun fradelSaksbehandlerAndMaybeSetHjemler(
        behandlingId: UUID,
        tildeltSaksbehandlerIdent: String?,
        enhetId: String?,
        fradelingReason: FradelingReason,
        utfoerendeSaksbehandlerIdent: String,
        hjemmelIdList: List<String>?,
    ) {
        if (fradelingReason == FradelingReason.FEIL_HJEMMEL) {
            if (hjemmelIdList.isNullOrEmpty()) {
                throw IllegalOperation("Hjemmel må velges når årsak til fradeling er \"Feil hjemmel\"")
            }
            setInnsendingshjemler(
                behandlingId = behandlingId,
                hjemler = hjemmelIdList,
                utfoerendeSaksbehandlerIdent = utfoerendeSaksbehandlerIdent,
            )
        }

        if (fradelingReason == FradelingReason.ANGRET) {
            val behandling = getBehandlingForReadWithoutCheckForAccess(behandlingId)
            if (behandling.tildeling!!.tidspunkt.isBefore(LocalDateTime.now().minusSeconds(30))) {
                throw MissingTilgangException("Det er for sent å angre tildelingen. Du må velge en grunn.")
            }
        }

        setSaksbehandler(
            behandlingId = behandlingId,
            tildeltSaksbehandlerIdent = null,
            enhetId = null,
            fradelingReason = fradelingReason,
            utfoerendeSaksbehandlerIdent = utfoerendeSaksbehandlerIdent,
            fradelingWithChangedHjemmelIdList = if (!hjemmelIdList.isNullOrEmpty()) hjemmelIdList.joinToString(",") else null,
        )
    }

    fun setSaksbehandler(
        behandlingId: UUID,
        tildeltSaksbehandlerIdent: String?,
        enhetId: String?,
        fradelingReason: FradelingReason?,
        utfoerendeSaksbehandlerIdent: String,
        fradelingWithChangedHjemmelIdList: String? = null,
        systemUserContext: Boolean = false,
    ): SaksbehandlerViewWrapped {
        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId,
            ignoreCheckSkrivetilgang = true,
            systemUserContext = systemUserContext
        )
        if (tildeltSaksbehandlerIdent != null) {
            //Denne sjekken gjøres kun når det er en tildeling:

            if (!systemUserContext) {
                checkYtelseAccess(tildeltSaksbehandlerIdent = tildeltSaksbehandlerIdent, behandling = behandling)
            }

            //if fagsystem is Infotrygd also do this.
            if (behandling.shouldUpdateInfotrygd()) {
                logger.debug("Tildeling av behandling skal registreres i Infotrygd.")
                fssProxyClient.setToAssigned(
                    sakId = behandling.kildeReferanse,
                    input = SakAssignedInput(
                        saksbehandlerIdent = tildeltSaksbehandlerIdent,
                        enhetsnummer = enhetId,
                    )
                )
                logger.debug("Tildeling av behandling ble registrert i Infotrygd.")
            }

            if (tildeltSaksbehandlerIdent == behandling.medunderskriver?.saksbehandlerident) {
                setMedunderskriverFlowState(
                    behandlingId = behandlingId,
                    utfoerendeSaksbehandlerIdent = utfoerendeSaksbehandlerIdent,
                    flowState = FlowState.NOT_SENT,
                )
                setMedunderskriverNavIdent(
                    behandlingId = behandlingId,
                    utfoerendeSaksbehandlerIdent = utfoerendeSaksbehandlerIdent,
                    navIdent = null,
                )
            }
        } else {
            if (fradelingReason == null &&
                !innloggetSaksbehandlerService.hasKabalInnsynEgenEnhetRole() &&
                !innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter()
            ) {
                throw IllegalOperation("Kun de med rollen 'innsyn egen enhet' eller 'oppgavestyring alle enheter' kan fradele behandling uten å oppgi årsak.")
            }

            if (behandling.medunderskriverFlowState == FlowState.SENT) {
                throw IllegalOperation("Kan ikke fradele behandling sendt til medunderskriver.")
            }

            //if fagsystem is Infotrygd also do this.
            if (behandling.shouldUpdateInfotrygd() && behandling.type != Type.ANKE_I_TRYGDERETTEN) {
                logger.debug("Fradeling av behandling skal registreres i Infotrygd.")
                fssProxyClient.setToHandledInKabal(
                    sakId = behandling.kildeReferanse,
                    input = HandledInKabalInput(
                        fristAsString = behandling.frist!!.format(DateTimeFormatter.BASIC_ISO_DATE),
                    )
                )
                logger.debug("Fradeling av behandling ble registrert i Infotrygd.")
            }

            if (behandling.sattPaaVent != null) {
                //Fjern på vent-status
                setSattPaaVent(
                    behandlingId = behandlingId,
                    utfoerendeSaksbehandlerIdent = utfoerendeSaksbehandlerIdent,
                    systemUserContext = saksbehandlerService.hasKabalOppgavestyringAlleEnheterRole(
                        utfoerendeSaksbehandlerIdent
                    ),
                    input = null,
                )
            }

        }

        val event =
            behandling.setTildeling(
                nyVerdiSaksbehandlerident = tildeltSaksbehandlerIdent,
                nyVerdiEnhet = enhetId,
                fradelingReason = fradelingReason,
                utfoerendeIdent = utfoerendeSaksbehandlerIdent,
                utfoerendeNavn = getUtfoerendeNavn(utfoerendeSaksbehandlerIdent),
                fradelingWithChangedHjemmelIdList = fradelingWithChangedHjemmelIdList,
            )
        applicationEventPublisher.publishEvent(event)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                TildelingEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = behandling.modified,
                    saksbehandler = if (tildeltSaksbehandlerIdent != null) {
                        Employee(
                            navIdent = tildeltSaksbehandlerIdent,
                            navn = saksbehandlerService.getNameForIdentDefaultIfNull(tildeltSaksbehandlerIdent),
                        )
                    } else null,
                    hjemmelIdList = fradelingWithChangedHjemmelIdList?.split(",") ?: emptyList(),
                    fradelingReasonId = fradelingReason?.id,
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.TILDELING,
        )

        return getSaksbehandlerViewWrapped(behandling)
    }

    fun setGosysOppgaveIdFromKabin(
        behandlingId: UUID,
        gosysOppgaveId: Long,
        utfoerendeSaksbehandlerIdent: String,
    ): LocalDateTime {
        if (!innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter()) {
            throw MissingTilgangException("$utfoerendeSaksbehandlerIdent does not have the right to modify oppgaveId")
        }

        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId,
            ignoreCheckSkrivetilgang = true
        )
        val event = behandling.setGosysOppgaveId(
            nyVerdi = gosysOppgaveId,
            saksbehandlerident = utfoerendeSaksbehandlerIdent
        )

        applicationEventPublisher.publishEvent(event)
        return behandling.modified
    }

    fun setOpprinneligVarsletFrist(
        behandlingstidUnitType: TimeUnitType,
        behandlingstidUnits: Int,
        behandlingId: UUID,
        systemUserContext: Boolean,
        mottakere: List<Mottaker>,
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId,
            ignoreCheckSkrivetilgang = true,
            systemUserContext = systemUserContext
        )
        //TODO differentiate between mottatt and now.
        val varsletFrist = findDateBasedOnTimeUnitTypeAndUnits(
            timeUnitType = behandlingstidUnitType,
            units = behandlingstidUnits,
            fromDate = behandling.mottattKlageinstans.toLocalDate()
        )

        return privateSetVarsletFrist(
            systemUserContext = systemUserContext,
            varsletFrist = varsletFrist,
            behandlingstidUnits = behandlingstidUnits,
            behandlingstidUnitType = behandlingstidUnitType,
            behandling = behandling,
            mottakere = mottakere,
            varselType = VarsletBehandlingstid.VarselType.OPPRINNELIG,
            doNotSendLetter = false,
            reasonNoLetter = null,
        )
    }

    private fun privateSetVarsletFrist(
        systemUserContext: Boolean,
        varsletFrist: LocalDate,
        behandlingstidUnits: Int?,
        behandlingstidUnitType: TimeUnitType?,
        behandling: Behandling,
        mottakere: List<Mottaker>,
        varselType: VarsletBehandlingstid.VarselType,
        doNotSendLetter: Boolean,
        reasonNoLetter: String?,
    ): LocalDateTime {
        val saksbehandlerIdent = if (systemUserContext) systembrukerIdent else tokenUtil.getIdent()

        val varsletBehandlingstid = VarsletBehandlingstid(
            varsletFrist = varsletFrist,
            varsletBehandlingstidUnits = if (behandlingstidUnitType != null) behandlingstidUnits else null,
            varsletBehandlingstidUnitType = if (behandlingstidUnits != null) behandlingstidUnitType else null,
            varselType = varselType,
            doNotSendLetter = doNotSendLetter,
            reasonNoLetter = reasonNoLetter,
        )

        if (behandling is BehandlingWithVarsletBehandlingstid) {
            applicationEventPublisher.publishEvent(
                behandling.setVarsletBehandlingstid(
                    varsletBehandlingstid = varsletBehandlingstid,
                    saksbehandlerident = saksbehandlerIdent,
                    saksbehandlernavn = getUtfoerendeNavn(saksbehandlerIdent),
                    mottakere = mottakere,
                )
            )
        } else {
            throw IllegalOperation("Behandlingstid kan ikke endres for denne behandlingstypen: ${behandling.javaClass.name}.")
        }

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                VarsletFristEvent(
                    actor = Employee(
                        navIdent = saksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(saksbehandlerIdent),
                    ),
                    timestamp = behandling.modified,
                    varsletFrist = varsletFrist,
                    timesPreviouslyExtended = behandling.getTimesPreviouslyExtended(),
                )
            ),
            behandlingId = behandling.id,
            type = InternalEventType.VARSLET_FRIST,
        )

        if (behandling.gosysOppgaveId != null) {
            gosysOppgaveService.updateVarsletFristInGosysOppgave(
                behandling = behandling,
                systemContext = systemUserContext,
                throwExceptionIfFerdigstilt = false
            )
        }

        return behandling.modified
    }

    fun setForlengetBehandlingstid(
        varsletFrist: LocalDate?,
        varsletBehandlingstidUnits: Int?,
        varsletBehandlingstidUnitType: TimeUnitType,
        behandling: Behandling,
        systemUserContext: Boolean,
        mottakere: List<Mottaker>,
        doNotSendLetter: Boolean,
        reasonNoLetter: String?,
    ): LocalDateTime {
        val newVarsletFrist =
            (varsletFrist
                ?: findDateBasedOnTimeUnitTypeAndUnits(
                    timeUnitType = varsletBehandlingstidUnitType,
                    units = varsletBehandlingstidUnits!!,
                    fromDate = LocalDate.now(),
                ))

        return privateSetVarsletFrist(
            systemUserContext = systemUserContext,
            varsletFrist = newVarsletFrist,
            behandlingstidUnits = varsletBehandlingstidUnits,
            behandlingstidUnitType = varsletBehandlingstidUnitType,
            behandling = behandling,
            mottakere = mottakere,
            varselType = VarsletBehandlingstid.VarselType.FORLENGET,
            doNotSendLetter = doNotSendLetter,
            reasonNoLetter = reasonNoLetter,
        )
    }

    fun setExpiredTildeltSaksbehandlerToNullInSystemContext(
        behandlingId: UUID,
    ) {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId, systemUserContext = true)

        if (behandling.medunderskriver != null) {
            logger.debug("Setter medunderskriver til null fordi saksbehandler settes til null i opprydding")
            setMedunderskriverToNullInSystemContext(behandlingId)
        }

        if (behandling.rolIdent != null) {
            logger.debug("Setter ROL til null fordi saksbehandler settes til null i opprydding")
            setRolToNullInSystemContext(behandlingId)
        }

        //if fagsystem is Infotrygd also do this.
        if (behandling.shouldUpdateInfotrygd() && behandling.type != Type.ANKE_I_TRYGDERETTEN) {
            logger.debug("Fradeling av behandling skal registreres i Infotrygd.")
            fssProxyClient.setToHandledInKabal(
                sakId = behandling.kildeReferanse,
                input = HandledInKabalInput(
                    fristAsString = behandling.frist!!.format(DateTimeFormatter.BASIC_ISO_DATE),
                )
            )
            logger.debug("Fradeling av behandling ble registrert i Infotrygd.")
        }

        if (behandling.sattPaaVent != null) {
            //Fjern på vent-status
            setSattPaaVent(
                behandlingId = behandlingId,
                utfoerendeSaksbehandlerIdent = systembrukerIdent,
                systemUserContext = true,
                input = null,
            )
        }

        val event =
            behandling.setTildeling(
                nyVerdiSaksbehandlerident = null,
                nyVerdiEnhet = null,
                fradelingReason = FradelingReason.UTGAATT,
                utfoerendeIdent = systembrukerIdent,
                utfoerendeNavn = systembrukerIdent,
                fradelingWithChangedHjemmelIdList = null,
            )
        applicationEventPublisher.publishEvent(event)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                TildelingEvent(
                    actor = Employee(
                        navIdent = systembrukerIdent,
                        navn = systembrukerIdent,
                    ),
                    timestamp = behandling.modified,
                    saksbehandler = null,
                    hjemmelIdList = emptyList(),
                    fradelingReasonId = FradelingReason.UTGAATT.id,
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.TILDELING,
        )
    }

    fun setMedunderskriverToNullInSystemContext(
        behandlingId: UUID,
    ) {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId, systemUserContext = true)

        if (behandling.medunderskriverFlowState != FlowState.RETURNED) {
            val medunderskriverFlowEvent =
                behandling.setMedunderskriverFlowState(
                    nyMedunderskriverFlowState = FlowState.NOT_SENT,
                    utfoerendeIdent = systembrukerIdent,
                    utfoerendeNavn = systembrukerIdent
                )
            applicationEventPublisher.publishEvent(medunderskriverFlowEvent)
        }

        val medunderskriverIdentEvent =
            behandling.setMedunderskriverNavIdent(
                nyMedunderskriverNavIdent = null,
                utfoerendeIdent = systembrukerIdent,
                utfoerendeNavn = systembrukerIdent
            )
        applicationEventPublisher.publishEvent(medunderskriverIdentEvent)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                MedunderskriverEvent(
                    actor = Employee(
                        navIdent = systembrukerIdent,
                        navn = systembrukerIdent,
                    ),
                    timestamp = behandling.modified,
                    medunderskriver = null,
                    flowState = FlowState.NOT_SENT,
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.MEDUNDERSKRIVER,
        )
    }

    fun setRolToNullInSystemContext(
        behandlingId: UUID,
    ) {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId, systemUserContext = true)

        if (behandling.rolFlowState != FlowState.RETURNED) {
            val rolFlowStateEvent =
                behandling.setROLFlowState(
                    newROLFlowStateState = FlowState.NOT_SENT,
                    utfoerendeIdent = systembrukerIdent,
                    utfoerendeNavn = systembrukerIdent
                )
            applicationEventPublisher.publishEvent(rolFlowStateEvent)

            if (behandling.rolReturnedDate != null) {
                val rolReturnedDateEvent =
                    behandling.setROLReturnedDate(
                        setNull = true,
                        utfoerendeIdent = systembrukerIdent,
                    )
                applicationEventPublisher.publishEvent(rolReturnedDateEvent)
            }
        }

        val rolIdentEvent =
            behandling.setROLIdent(
                newROLIdent = null,
                utfoerendeIdent = systembrukerIdent,
                utfoerendeNavn = systembrukerIdent,
            )
        applicationEventPublisher.publishEvent(rolIdentEvent)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                RolEvent(
                    actor = Employee(
                        navIdent = systembrukerIdent,
                        navn = systembrukerIdent,
                    ),
                    timestamp = behandling.modified,
                    rol = null,
                    flowState = FlowState.NOT_SENT,
                    returnDate = null,
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.ROL,
        )
    }


    fun getSaksbehandler(behandlingId: UUID): SaksbehandlerViewWrapped {
        return getSaksbehandlerViewWrapped(getBehandlingAndCheckLeseTilgangForPerson((behandlingId)))
    }

    private fun getSaksbehandlerViewWrapped(behandling: Behandling): SaksbehandlerViewWrapped {
        return SaksbehandlerViewWrapped(
            saksbehandler = getSaksbehandlerView(behandling),
            modified = behandling.modified,
        )
    }

    private fun getSaksbehandlerView(behandling: Behandling): SaksbehandlerView? {
        val saksbehandlerView = if (behandling.tildeling?.saksbehandlerident == null) {
            null
        } else {
            SaksbehandlerView(
                navIdent = behandling.tildeling?.saksbehandlerident!!,
                navn = saksbehandlerService.getNameForIdentDefaultIfNull(behandling.tildeling?.saksbehandlerident!!),
            )
        }
        return saksbehandlerView
    }

    fun getMedunderskriver(behandlingId: UUID): MedunderskriverWrapped {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        return behandlingMapper.mapToMedunderskriverWrapped(behandling)
    }

    fun getMedunderskriverFlowState(behandlingId: UUID): FlowStateView {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        return behandlingMapper.mapToMedunderskriverFlowStateView(behandling)
    }

    fun setSattPaaVent(
        behandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String,
        systemUserContext: Boolean = false,
        input: SattPaaVentInput?
    ): LocalDateTime {
        val sattPaaVent = if (input != null) {
            SattPaaVent(
                from = LocalDate.now(),
                to = input.to,
                reason = input.reason,
            )
        } else null

        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId,
            systemUserContext = systemUserContext ||
                    saksbehandlerService.hasKabalOppgavestyringAlleEnheterRole(
                        utfoerendeSaksbehandlerIdent
                    )
        )
        val event =
            behandling.setSattPaaVent(
                nyVerdi = sattPaaVent,
                utfoerendeIdent = utfoerendeSaksbehandlerIdent,
                utfoerendeNavn = getUtfoerendeNavn(utfoerendeSaksbehandlerIdent),
            )
        applicationEventPublisher.publishEvent(event)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                SattPaaVentEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = if (utfoerendeSaksbehandlerIdent == systembrukerIdent) {
                            utfoerendeSaksbehandlerIdent
                        } else saksbehandlerService.getNameForIdentDefaultIfNull(
                            utfoerendeSaksbehandlerIdent
                        ),
                    ),
                    timestamp = behandling.modified,
                    sattPaaVent = behandling.sattPaaVent?.let {
                        SattPaaVentEvent.SattPaaVent(
                            from = it.from,
                            to = it.to,
                            reason = it.reason,
                        )
                    }
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.SATT_PAA_VENT,
        )

        return behandling.modified
    }

    fun setFrist(
        behandlingId: UUID,
        frist: LocalDate,
        utfoerendeSaksbehandlerIdent: String,
    ): LocalDateTime {
        if (!innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter()) {
            throw MissingTilgangException("$utfoerendeSaksbehandlerIdent does not have the right to modify frist")
        }

        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId,
            ignoreCheckSkrivetilgang = true
        )
        val event = behandling.setFrist(frist, utfoerendeSaksbehandlerIdent)

        applicationEventPublisher.publishEvent(event)
        return behandling.modified
    }

    fun setMottattKlageinstans(
        behandlingId: UUID,
        date: LocalDateTime,
        utfoerendeSaksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = if (innloggetSaksbehandlerService.isKabalOppgavestyringAlleEnheter()) {
            getBehandlingForUpdate(
                behandlingId = behandlingId,
                ignoreCheckSkrivetilgang = true,
            )
        } else {
            getBehandlingForUpdate(behandlingId)
        }

        val event = behandling.setMottattKlageinstans(date, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return behandling.modified
    }

    fun setMottattVedtaksinstans(
        behandlingId: UUID,
        date: LocalDate,
        utfoerendeSaksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(
            behandlingId
        )

        if (behandling is Klagebehandling) {
            val event =
                behandling.setMottattVedtaksinstans(date, utfoerendeSaksbehandlerIdent)
            applicationEventPublisher.publishEvent(event)

            publishInternalEvent(
                data = objectMapper.writeValueAsString(
                    MottattVedtaksinstansEvent(
                        actor = Employee(
                            navIdent = utfoerendeSaksbehandlerIdent,
                            navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                        ),
                        timestamp = behandling.modified,
                        mottattVedtaksinstans = behandling.mottattVedtaksinstans,
                    )
                ),
                behandlingId = behandlingId,
                type = InternalEventType.MOTTATT_VEDTAKSINSTANS,
            )

            return behandling.modified
        } else throw IllegalOperation("Dette feltet kan bare settes i klagesaker")
    }

    fun setSendtTilTrygderetten(
        behandlingId: UUID,
        date: LocalDateTime,
        utfoerendeSaksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(
            behandlingId
        )

        if (behandling is AnkeITrygderettenbehandling) {
            val event =
                behandling.setSendtTilTrygderetten(date, utfoerendeSaksbehandlerIdent)
            applicationEventPublisher.publishEvent(event)
            return behandling.modified
        } else throw IllegalOperation("Dette feltet kan bare settes i ankesaker i Trygderetten")
    }

    fun setKjennelseMottatt(
        behandlingId: UUID,
        date: LocalDateTime?,
        utfoerendeSaksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(
            behandlingId
        )

        if (behandling is AnkeITrygderettenbehandling) {
            val event =
                behandling.setKjennelseMottatt(date, utfoerendeSaksbehandlerIdent)
            applicationEventPublisher.publishEvent(event)
            return behandling.modified
        } else throw IllegalOperation("Dette feltet kan bare settes i ankesaker i Trygderetten")
    }

    fun setNyAnkebehandlingKAAndSetToAvsluttet(
        behandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId)

        if (behandling is AnkeITrygderettenbehandling) {
            val eventNyBehandling = behandling.setNyAnkebehandlingKA(LocalDateTime.now(), utfoerendeSaksbehandlerIdent)
            val eventAvsluttetAvSaksbehandler = behandling.setAvsluttetAvSaksbehandler(
                saksbehandlerident = utfoerendeSaksbehandlerIdent,
                saksbehandlernavn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
            )
            val endringslogginnslag =
                eventNyBehandling.endringslogginnslag + eventAvsluttetAvSaksbehandler.endringslogginnslag
            applicationEventPublisher.publishEvent(
                BehandlingEndretEvent(
                    behandling = behandling,
                    endringslogginnslag = endringslogginnslag
                )
            )

            return behandling
        } else throw IllegalOperation("Dette feltet kan bare settes i ankesaker i Trygderetten")
    }

    fun setNyBehandlingEtterTROpphevetAndSetToAvsluttet(
        behandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId)

        if (behandling is AnkeITrygderettenbehandling) {
            val eventNyBehandling =
                behandling.setNyBehandlingEtterTROpphevet(LocalDateTime.now(), utfoerendeSaksbehandlerIdent)
            val eventAvsluttetAvSaksbehandler = behandling.setAvsluttetAvSaksbehandler(
                saksbehandlerident = utfoerendeSaksbehandlerIdent,
                saksbehandlernavn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
            )
            val endringslogginnslag =
                eventNyBehandling.endringslogginnslag + eventAvsluttetAvSaksbehandler.endringslogginnslag
            applicationEventPublisher.publishEvent(
                BehandlingEndretEvent(
                    behandling = behandling,
                    endringslogginnslag = endringslogginnslag
                )
            )

            return behandling
        } else throw IllegalOperation("Dette feltet kan bare settes i ankesaker i Trygderetten")
    }

    fun setInnsendingshjemler(
        behandlingId: UUID,
        hjemler: List<String>,
        utfoerendeSaksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId,
            ignoreCheckSkrivetilgang = true,
        )

        if (behandling.ferdigstilling != null) {
            throw BehandlingAvsluttetException("Kan ikke endre avsluttet behandling")
        }

        val event =
            behandling.setInnsendingshjemler(
                hjemler.map { Hjemmel.of(it) }.toSet(),
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                InnsendingshjemlerEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = behandling.modified,
                    hjemmelIdSet = behandling.hjemler.map { it.id }.toSet(),
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.INNSENDINGSHJEMLER,
        )

        return behandling.modified
    }

    fun setFullmektig(
        behandlingId: UUID,
        input: FullmektigInput,
        utfoerendeSaksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(
            behandlingId
        )

        if (behandling.prosessfullmektig == null) {
            if (input.identifikator == null && input.address == null && input.name == null) {
                throw IllegalOperation("Fullmektig er allerede fjernet")
            }
        }

        if (behandling.prosessfullmektig?.partId?.value != null && behandling.prosessfullmektig?.partId?.value == input.identifikator) {
            throw IllegalOperation("Denne fullmektigen er allerede satt")
        }

        if (input.identifikator != null && (input.address != null || input.name != null)) {
            throw IllegalOperation("Address and name can only be set without id")
        }

        if ((input.address != null && input.name == null) || (input.address == null && input.name != null)) {
            throw IllegalOperation("Both address or name must be set")
        }

        if (input.address != null) {
            input.address.validateAddress()
        }

        if (input.identifikator != null && input.identifikator in listOf(
                behandling.sakenGjelder.partId.value,
            )
        ) {
            throw IllegalOperation("Fullmektig kan ikke være den samme som den saken gjelder.")
        }

        val partId: PartId? = if (input.identifikator == null) {
            null
        } else {
            getPartIdFromIdentifikator(input.identifikator)
        }

        val event =
            behandling.setFullmektig(
                partId = partId,
                name = input.name,
                address = input.address?.let {
                    val poststed = if (it.landkode == "NO") {
                        if (it.postnummer != null) {
                            kodeverkService.getPoststed(it.postnummer)
                        } else throw IllegalOperation("Postnummer must be set for Norwegian address")
                    } else null
                    Adresse(
                        adresselinje1 = it.adresselinje1,
                        adresselinje2 = it.adresselinje2,
                        adresselinje3 = it.adresselinje3,
                        postnummer = it.postnummer,
                        poststed = poststed,
                        landkode = it.landkode,
                    )
                },

                utfoerendeIdent = utfoerendeSaksbehandlerIdent,
                utfoerendeNavn = getUtfoerendeNavn(utfoerendeSaksbehandlerIdent),
            )
        applicationEventPublisher.publishEvent(event)

        val partView = if (behandling.prosessfullmektig == null) {
            null
        } else if (behandling.prosessfullmektig!!.partId != null) {
            val searchPartViewWithUtsendingskanal = partSearchService.searchPartWithUtsendingskanal(
                identifikator = behandling.prosessfullmektig?.partId?.value!!,
                systemUserContext = true,
                sakenGjelderId = behandling.sakenGjelder.partId.value,
                tema = behandling.ytelse.toTema(),
                systemContext = false,
            )

            BehandlingDetaljerView.PartViewWithUtsendingskanal(
                id = behandling.prosessfullmektig!!.id,
                identifikator = searchPartViewWithUtsendingskanal.identifikator,
                name = searchPartViewWithUtsendingskanal.name,
                type = searchPartViewWithUtsendingskanal.type,
                available = searchPartViewWithUtsendingskanal.available,
                language = searchPartViewWithUtsendingskanal.language,
                statusList = searchPartViewWithUtsendingskanal.statusList,
                address = searchPartViewWithUtsendingskanal.address,
                utsendingskanal = searchPartViewWithUtsendingskanal.utsendingskanal,
            )
        } else {
            BehandlingDetaljerView.PartViewWithUtsendingskanal(
                id = behandling.prosessfullmektig!!.id,
                identifikator = null,
                name = behandling.prosessfullmektig!!.navn!!,
                type = null,
                available = true,
                language = null,
                statusList = listOf(),
                address = BehandlingDetaljerView.Address(
                    adresselinje1 = behandling.prosessfullmektig!!.address!!.adresselinje1,
                    adresselinje2 = behandling.prosessfullmektig!!.address!!.adresselinje2,
                    adresselinje3 = behandling.prosessfullmektig!!.address!!.adresselinje3,
                    landkode = behandling.prosessfullmektig!!.address!!.landkode,
                    postnummer = behandling.prosessfullmektig!!.address!!.postnummer,
                    poststed = behandling.prosessfullmektig!!.address!!.poststed,
                ),
                utsendingskanal = BehandlingDetaljerView.Utsendingskanal.SENTRAL_UTSKRIFT
            )
        }

        if (behandling is BehandlingWithVarsletBehandlingstid && behandling.forlengetBehandlingstidDraft != null) {
            behandling.forlengetBehandlingstidDraft!!.fullmektigFritekst = partView?.name
        }

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                FullmektigEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = behandling.modified,
                    part = partView?.let {
                        Part(
                            id = behandling.prosessfullmektig?.id!!,
                            identifikator = partView.identifikator,
                            type = partView.type,
                            name = partView.name,
                            statusList = partView.statusList,
                            available = partView.available,
                            address = partView.address,
                            language = partView.language,
                            utsendingskanal = partView.utsendingskanal
                        )
                    },
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.FULLMEKTIG,
        )

        return behandling.modified
    }

    fun setKlager(
        behandlingId: UUID,
        identifikator: String,
        utfoerendeSaksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(
            behandlingId
        )

        if (behandling.klager.partId.value == identifikator) {
            throw IllegalOperation("Denne klageparten er allerede satt")
        }

        val event =
            behandling.setKlager(
                nyVerdi = getPartIdFromIdentifikator(identifikator),
                utfoerendeIdent = utfoerendeSaksbehandlerIdent,
                utfoerendeNavn = getUtfoerendeNavn(utfoerendeSaksbehandlerIdent),
            )
        applicationEventPublisher.publishEvent(event)

        val partView =
            partSearchService.searchPartWithUtsendingskanal(
                identifikator = behandling.klager.partId.value,
                systemUserContext = true,
                sakenGjelderId = behandling.sakenGjelder.partId.value,
                tema = behandling.ytelse.toTema(),
                systemContext = false,
            )

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                KlagerEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = behandling.modified,
                    part = Part(
                        id = behandling.klager.id,
                        identifikator = partView.identifikator,
                        type = partView.type,
                        name = partView.name,
                        statusList = partView.statusList,
                        available = partView.available,
                        language = partView.language,
                        address = partView.address,
                        utsendingskanal = partView.utsendingskanal,
                    ),
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.KLAGER,
        )

        return behandling.modified
    }

    fun setTilbakekreving(
        behandlingId: UUID,
        tilbakekreving: Boolean,
        utfoerendeSaksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(
            behandlingId
        )

        val event =
            behandling.setTilbakekreving(
                nyVerdi = tilbakekreving,
                saksbehandlerident = utfoerendeSaksbehandlerIdent,
            )
        applicationEventPublisher.publishEvent(event)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                TilbakekrevingEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = behandling.modified,
                    tilbakekreving = behandling.tilbakekreving,
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.TILBAKEKREVING,
        )

        return behandling.modified
    }

    fun setMedunderskriverFlowState(
        behandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String,
        flowState: FlowState,
    ): MedunderskriverWrapped {
        val behandling = getBehandlingForWriteAllowROLAndMU(
            behandlingId = behandlingId,
            utfoerendeSaksbehandlerIdent = utfoerendeSaksbehandlerIdent,
        )

        val event =
            behandling.setMedunderskriverFlowState(
                nyMedunderskriverFlowState = flowState,
                utfoerendeIdent = utfoerendeSaksbehandlerIdent,
                utfoerendeNavn = getUtfoerendeNavn(utfoerendeSaksbehandlerIdent),
            )
        applicationEventPublisher.publishEvent(event)

        val medunderskriverWrapped = behandlingMapper.mapToMedunderskriverWrapped(behandling)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                MedunderskriverEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    medunderskriver = if (medunderskriverWrapped.employee != null) {
                        Employee(
                            navIdent = medunderskriverWrapped.employee.navIdent,
                            navn = medunderskriverWrapped.employee.navn,
                        )
                    } else null,
                    timestamp = medunderskriverWrapped.modified,
                    flowState = medunderskriverWrapped.flowState,
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.MEDUNDERSKRIVER,
        )

        return medunderskriverWrapped
    }

    fun setMedunderskriverNavIdent(
        behandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String,
        navIdent: String?,
    ): MedunderskriverWrapped {
        val behandling =
            if (saksbehandlerService.hasKabalOppgavestyringAlleEnheterRole(utfoerendeSaksbehandlerIdent)) {
                val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
                if (behandling.medunderskriverFlowState != FlowState.SENT && behandling.tildeling?.saksbehandlerident != utfoerendeSaksbehandlerIdent) {
                    throw MissingTilgangException("OppgavestyringAlleEnheter har ikke lov til å endre medunderskriver når den ikke er sent.")
                }

                if (behandling.tildeling?.saksbehandlerident != utfoerendeSaksbehandlerIdent && navIdent == null) {
                    throw MissingTilgangException("Kun saksbehandler har lov til å nullstille medunderskriver.")
                }

                getBehandlingForUpdate(behandlingId = behandlingId, ignoreCheckSkrivetilgang = true)
            } else {
                getBehandlingForWriteAllowROLAndMU(
                    behandlingId = behandlingId,
                    utfoerendeSaksbehandlerIdent = utfoerendeSaksbehandlerIdent
                )
            }

        if (navIdent != null && behandling.tildeling?.saksbehandlerident == navIdent) {
            throw IllegalOperation("Medunderskriver kan ikke være lik saksbehandler")
        }

        val event =
            behandling.setMedunderskriverNavIdent(
                nyMedunderskriverNavIdent = navIdent,
                utfoerendeIdent = utfoerendeSaksbehandlerIdent,
                utfoerendeNavn = getUtfoerendeNavn(utfoerendeSaksbehandlerIdent),
            )
        applicationEventPublisher.publishEvent(event)

        val medunderskriverWrapped = behandlingMapper.mapToMedunderskriverWrapped(behandling)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                MedunderskriverEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = medunderskriverWrapped.modified,
                    medunderskriver = if (medunderskriverWrapped.employee != null) {
                        Employee(
                            navIdent = medunderskriverWrapped.employee.navIdent,
                            navn = medunderskriverWrapped.employee.navn,
                        )
                    } else null,
                    flowState = medunderskriverWrapped.flowState,
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.MEDUNDERSKRIVER,
        )

        return medunderskriverWrapped
    }

    private fun publishInternalEvent(data: String, behandlingId: UUID, type: InternalEventType) {
        kafkaInternalEventService.publishInternalBehandlingEvent(
            InternalBehandlingEvent(
                behandlingId = behandlingId.toString(),
                type = type,
                data = data,
            )
        )
    }

    fun fetchDokumentlisteForBehandling(
        behandlingId: UUID,
        temaer: List<Tema>,
        pageSize: Int,
        previousPageRef: String?
    ): DokumenterResponse {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        return dokumentService.fetchDokumentlisteForBehandling(behandling, temaer, pageSize, previousPageRef)
    }

//    fun connectDokumentToBehandling(
//        behandlingId: UUID,
//        journalpostId: String,
//        dokumentInfoId: String,
//        saksbehandlerIdent: String,
//        systemUserContext: Boolean = false,
//        ignoreCheckSkrivetilgang: Boolean,
//    ): LocalDateTime {
//        val behandling = getBehandlingForUpdate(
//            behandlingId = behandlingId,
//            ignoreCheckSkrivetilgang = ignoreCheckSkrivetilgang,
//            systemUserContext = systemUserContext,
//        )
//
//        addDokumentList(
//            behandling,
//            journalpostId,
//            dokumentInfoId,
//            saksbehandlerIdent
//        )
//        return behandling.modified
//    }

    fun connectDocumentsToBehandling(
        behandlingId: UUID,
        journalfoertDokumentReferenceSet: Set<JournalfoertDokumentReference>,
        saksbehandlerIdent: String,
        systemUserContext: Boolean = false,
        ignoreCheckSkrivetilgang: Boolean,
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId,
            ignoreCheckSkrivetilgang = ignoreCheckSkrivetilgang,
            systemUserContext = systemUserContext,
        )

        val journalpostListForUser = safFacade.getJournalposter(
            journalpostIdSet = journalfoertDokumentReferenceSet.map { it.journalpostId }.toSet(),
            fnr = behandling.sakenGjelder.partId.value,
            saksbehandlerContext = !systemUserContext,
        )

        if (journalpostListForUser.any { it.journalstatus == Journalstatus.MOTTATT }) {
            throw DokumentValidationException("Kan ikke legge til journalførte dokumenter med status 'Mottatt' som relevant for saken. Fullfør journalføring i Gosys for å gjøre dette.")
        }

        addDokumentSet(
            behandling = behandling,
            journalfoertDokumentReferenceSet = journalfoertDokumentReferenceSet,
            saksbehandlerIdent = saksbehandlerIdent,
        )
        return behandling.modified
    }

    fun disconnectDokumentFromBehandling(
        behandlingId: UUID,
        journalpostId: String,
        dokumentInfoId: String,
        saksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(behandlingId)
        val saksdokument =
            behandling.saksdokumenter.find { it.journalpostId == journalpostId && it.dokumentInfoId == dokumentInfoId }

        if (saksdokument == null) {
            logger.warn("no saksdokument found based on id $journalpostId/$dokumentInfoId")
        } else {
            removeDokument(
                behandling,
                saksdokument,
                saksbehandlerIdent
            )
        }
        return behandling.modified
    }

    fun disconnectAllDokumenterFromBehandling(
        behandlingId: UUID,
        saksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(behandlingId)

        try {
            val event =
                behandling.clearSaksdokumenter(
                    saksbehandlerIdent
                )
            event.let { applicationEventPublisher.publishEvent(it) }

            return behandling.modified
        } catch (e: Exception) {
            logger.error("Error disconnecting all documents from behandling ${behandling.id}", e)
            throw e
        }
    }

    fun getBehandlingForUpdate(
        behandlingId: UUID,
        ignoreCheckSkrivetilgang: Boolean = false,
        systemUserContext: Boolean = false
    ): Behandling =
        behandlingRepository.findById(behandlingId).get()
            .also {
                if (!systemUserContext && it.feilregistrering != null) {
                    throw BehandlingAvsluttetException("Behandlingen er feilregistrert")
                }
            }
            .also { if (!systemUserContext) checkLesetilgangForPerson(it) }
            .also { if (!systemUserContext && !ignoreCheckSkrivetilgang) checkSkrivetilgang(it) }

    fun checkLesetilgangForPerson(behandling: Behandling) {
        if (behandling.sakenGjelder.erPerson()) {
            checkLesetilgangForPerson(behandling.sakenGjelder.partId.value)
        }
    }

    fun checkLesetilgangForPerson(partIdValue: String) {
        tilgangService.verifyInnloggetSaksbehandlersTilgangTil(partIdValue)
    }

    private fun checkSkrivetilgang(behandling: Behandling) {
        tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling)
    }

    private fun checkSkrivetilgangForSystembruker(behandling: Behandling) {
        tilgangService.checkIfBehandlingIsAvsluttet(behandling)
    }

    @Transactional(readOnly = true)
    fun getBehandlingForReadWithoutCheckForAccess(behandlingId: UUID): Behandling =
        behandlingRepository.findById(behandlingId)
            .orElseThrow { BehandlingNotFoundException("Behandling med id $behandlingId ikke funnet") }

    /**
     * Get behandling with eager loading of relations for read without checking access.
     */
    @Transactional(readOnly = true)
    fun getBehandlingEagerForReadWithoutCheckForAccess(behandlingId: UUID): Behandling =
        behandlingRepository.findByIdEager(behandlingId)

    fun getBehandlingForUpdateBySystembruker(
        behandlingId: UUID,
    ): Behandling =
        behandlingRepository.findById(behandlingId).get()
            .also { checkSkrivetilgangForSystembruker(it) }

    private fun checkYtelseAccess(
        tildeltSaksbehandlerIdent: String,
        behandling: Behandling
    ) {
        tilgangService.verifySaksbehandlersAccessToYtelse(
            saksbehandlerIdent = tildeltSaksbehandlerIdent,
            ytelse = behandling.ytelse,
        )
    }

    //TODO: Se om ansvar for sjekk av medunderskriver/rol og finalize kan deles opp.
    private fun verifyMedunderskriverStatusAndBehandlingNotFinalized(behandling: Behandling) {
        tilgangService.verifyInnloggetSaksbehandlerIsMedunderskriverOrROLAndNotFinalized(behandling)
    }

    private fun addDokumentSet(
        behandling: Behandling,
        journalfoertDokumentReferenceSet: Set<JournalfoertDokumentReference>,
        saksbehandlerIdent: String
    ) {
        val (existingSaksdokuments, saksdokumentsToAdd) = journalfoertDokumentReferenceSet.partition { journalfoerDokumentReference ->
            behandling.saksdokumenter.any {
                it.journalpostId == journalfoerDokumentReference.journalpostId && it.dokumentInfoId == journalfoerDokumentReference.dokumentInfoId
            }
        }

        if (existingSaksdokuments.isNotEmpty()) {
            logger.debug(
                "Already added documents in behandling {}: {}",
                behandling.id,
                existingSaksdokuments.joinToString()
            )
        }

        if (saksdokumentsToAdd.isNotEmpty()) {
            val event = behandling.addSaksdokumenter(
                saksdokumentList = saksdokumentsToAdd.map {
                    Saksdokument(journalpostId = it.journalpostId, dokumentInfoId = it.dokumentInfoId)
                },
                saksbehandlerident = saksbehandlerIdent
            )
            event.let { applicationEventPublisher.publishEvent(it) }
        }
    }

    private fun removeDokument(
        behandling: Behandling,
        saksdokument: Saksdokument,
        saksbehandlerIdent: String
    ): Behandling {
        try {
            val event =
                behandling.removeSaksdokument(
                    saksdokument,
                    saksbehandlerIdent
                )
            event.let { applicationEventPublisher.publishEvent(it) }

            return behandling
        } catch (e: Exception) {
            logger.error("Error disconnecting document ${saksdokument.id} to behandling ${behandling.id}", e)
            throw e
        }
    }

    @Transactional(readOnly = true)
    fun getBehandlingForWriteAllowROLAndMU(behandlingId: UUID, utfoerendeSaksbehandlerIdent: String): Behandling {
        val behandling = behandlingRepository.findById(behandlingId).get()
        if (behandling.medunderskriver?.saksbehandlerident == utfoerendeSaksbehandlerIdent || behandling.rolIdent == utfoerendeSaksbehandlerIdent) {
            verifyMedunderskriverStatusAndBehandlingNotFinalized(behandling)
        } else {
            checkSkrivetilgang(behandling)
        }
        return behandling
    }

    fun getBehandlingDetaljerView(behandlingId: UUID): BehandlingDetaljerView {
        return behandlingMapper.mapBehandlingToBehandlingDetaljerView(
            getBehandlingAndCheckLeseTilgangForPerson(
                behandlingId
            )
        )
    }

    fun getBehandlingROLView(behandlingId: UUID): RolView {
        return behandlingMapper.mapToRolView(getBehandlingAndCheckLeseTilgangForPerson(behandlingId))
    }

    fun getBehandlingOppgaveView(behandlingId: UUID): OppgaveView {
        return behandlingMapper.mapBehandlingToOppgaveView(getBehandlingAndCheckLeseTilgangForPerson(behandlingId))
    }

    @Transactional(readOnly = true)
    fun getBehandlingAndCheckLeseTilgangForPerson(behandlingId: UUID): Behandling =
        behandlingRepository.findById(behandlingId)
            .orElseThrow { BehandlingNotFoundException("Behandling med id $behandlingId ikke funnet") }
            .also { checkLesetilgangForPerson(it) }

    @Transactional(readOnly = true)
    fun findBehandlingerForAvslutning(): List<Pair<UUID, Type>> =
        behandlingRepository.findByFerdigstillingAvsluttetIsNullAndFerdigstillingAvsluttetAvSaksbehandlerIsNotNullAndFeilregistreringIsNull()
            .sortedByDescending { it.ferdigstilling?.avsluttetAvSaksbehandler }
            .map { it.id to it.type }

    fun getPotentialSaksbehandlereForBehandling(behandlingId: UUID): Saksbehandlere {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        return kabalInnstillingerService.getPotentialSaksbehandlere(behandling)
    }

    fun getPotentialMedunderskrivereForBehandling(behandlingId: UUID): Medunderskrivere {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        return kabalInnstillingerService.getPotentialMedunderskrivere(behandling)
    }

    fun getPotentialROLForBehandling(behandlingId: UUID): Rols {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        return Rols(
            rols = kabalInnstillingerService.getPotentialROL(behandling).saksbehandlere.map {
                Rols.Rol(
                    navIdent = it.navIdent,
                    navn = it.navn,
                )
            }
        )
    }

    fun getAllBehandlingerForEnhet(enhet: String): List<Behandling> {
        return behandlingRepository.findByTildelingEnhetAndFerdigstillingIsNullAndFeilregistreringIsNull(
            enhet
        )
    }

    fun getAInntektUrl(behandlingId: UUID): String {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId = behandlingId)
        return arbeidOgInntektClient.getAInntektUrl(behandling.sakenGjelder.partId.value)
    }

    fun getAARegisterUrl(behandlingId: UUID): String {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId = behandlingId)
        return arbeidOgInntektClient.getAARegisterUrl(behandling.sakenGjelder.partId.value)
    }

    fun feilregistrer(behandlingId: UUID, reason: String, fagsystem: Fagsystem): FeilregistreringResponse {
        val navIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        val behandlingForCheck = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        val behandling =
            if (saksbehandlerService.hasKabalOppgavestyringAlleEnheterRole(navIdent) || behandlingForCheck.tildeling == null) {
                getBehandlingForUpdate(behandlingId = behandlingId, ignoreCheckSkrivetilgang = true)
            } else {
                getBehandlingForUpdate(behandlingId = behandlingId)
            }

        val modifiedBehandling =
            feilregistrer(behandling = behandling, navIdent = navIdent, reason = reason, fagsystem = fagsystem)

        return FeilregistreringResponse(
            feilregistrering = BehandlingDetaljerView.FeilregistreringView(
                feilregistrertAv = SaksbehandlerView(
                    navIdent = modifiedBehandling.feilregistrering!!.navIdent,
                    navn = saksbehandlerService.getNameForIdentDefaultIfNull(modifiedBehandling.feilregistrering!!.navIdent)
                ),
                registered = modifiedBehandling.feilregistrering!!.registered,
                reason = modifiedBehandling.feilregistrering!!.reason,
                fagsystemId = modifiedBehandling.feilregistrering!!.fagsystem.id
            ),
            modified = modifiedBehandling.modified,
        )
    }

    fun feilregistrer(
        type: Type,
        reason: String,
        fagsystem: Fagsystem,
        navIdent: String,
        kildereferanse: String
    ): Behandling {
        var candidates = behandlingRepository.findByFagsystemAndKildeReferanseAndFeilregistreringIsNullAndType(
            fagsystem = fagsystem,
            kildeReferanse = kildereferanse,
            type = type,
        )
        if (candidates.isEmpty()) {
            throw FeilregistreringException("Fant ingen saker å feilføre")
        }

        candidates = candidates.filter { it.ferdigstilling == null }

        if (candidates.isEmpty()) {
            throw FeilregistreringException("Kan ikke feilføre fullført sak")
        }

        candidates = candidates.filter { it.tildeling == null }

        if (candidates.isEmpty()) {
            throw FeilregistreringException("Kan ikke feilføre tildelt sak. Kontakt KA for feilregistrering.")
        }

        if (candidates.size > 1) {
            throw RuntimeException("Ended up with more than one candidate for feilregistrering. Kildereferanse: $kildereferanse")
        }

        return feilregistrer(
            behandling = candidates.first(),
            navIdent = navIdent,
            reason = reason,
            fagsystem = fagsystem,
            systemUserContext = true,
        )
    }

    private fun feilregistrer(
        behandling: Behandling,
        navIdent: String,
        reason: String,
        fagsystem: Fagsystem,
        systemUserContext: Boolean = false,
    ): Behandling {
        val navn = saksbehandlerService.getNameForIdentDefaultIfNull(navIdent)

        setSaksbehandler(
            behandlingId = behandling.id,
            tildeltSaksbehandlerIdent = null,
            enhetId = null,
            fradelingReason = FradelingReason.ANNET,
            utfoerendeSaksbehandlerIdent = navIdent,
            fradelingWithChangedHjemmelIdList = null,
            systemUserContext = systemUserContext,
        )

        val event = behandling.setFeilregistrering(
            feilregistrering = Feilregistrering(
                navIdent = navIdent,
                navn = navn,
                registered = LocalDateTime.now(),
                reason = reason,
                fagsystem = fagsystem,
            ),
            saksbehandlerident = navIdent,
        )
        applicationEventPublisher.publishEvent(event)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                FeilregistreringEvent(
                    actor = Employee(
                        navIdent = navIdent,
                        navn = navn,
                    ),
                    timestamp = behandling.modified,
                    registered = behandling.feilregistrering!!.registered,
                    reason = behandling.feilregistrering!!.reason,
                    fagsystemId = behandling.feilregistrering!!.fagsystem.id,
                ),
            ),
            behandlingId = behandling.id,
            type = InternalEventType.FEILREGISTRERING,
        )

        return behandling
    }

    fun setUtfall(
        behandlingId: UUID,
        utfall: Utfall?,
        utfoerendeSaksbehandlerIdent: String
    ): UtfallEditedView {
        logger.debug("Input utfall in setUtfall: {}", utfall)
        val behandling = getBehandlingForUpdate(
            behandlingId
        )
        val endringslogginnslag = mutableListOf<Endringslogginnslag>()

        if (utfall != null) {
            if (utfall in behandling.extraUtfallSet) {
                val event =
                    behandling.setExtraUtfallSet(
                        nyVerdi = behandling.extraUtfallSet.minus(utfall),
                        saksbehandlerident = utfoerendeSaksbehandlerIdent
                    )
                endringslogginnslag += event.endringslogginnslag
            }
        } else {
            val event =
                behandling.setExtraUtfallSet(
                    nyVerdi = setOf(),
                    saksbehandlerident = utfoerendeSaksbehandlerIdent
                )
            endringslogginnslag += event.endringslogginnslag
        }

        val event =
            behandling.setUtfall(
                nyVerdi = utfall,
                saksbehandlerident = utfoerendeSaksbehandlerIdent
            )
        endringslogginnslag += event.endringslogginnslag

        val groupedEvent = BehandlingEndretEvent(
            behandling = behandling,
            endringslogginnslag = endringslogginnslag,
        )
        applicationEventPublisher.publishEvent(groupedEvent)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                UtfallEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = behandling.modified,
                    utfallId = behandling.utfall?.id,
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.UTFALL,
        )

        return UtfallEditedView(
            modified = behandling.modified,
            utfallId = behandling.utfall?.id,
            extraUtfallIdSet = behandling.extraUtfallSet.map { it.id }.toSet(),
        )
    }

    fun setExtraUtfallSet(
        behandlingId: UUID,
        extraUtfallSet: Set<Utfall>,
        utfoerendeSaksbehandlerIdent: String
    ): ExtraUtfallEditedView {
        val behandling = getBehandlingForUpdate(
            behandlingId
        )

        val curatedExtraUtfallSet = if (behandling.utfall != null && behandling.utfall in extraUtfallSet) {
            extraUtfallSet.minus(behandling.utfall!!)
        } else extraUtfallSet

        val event =
            behandling.setExtraUtfallSet(
                nyVerdi = curatedExtraUtfallSet,
                saksbehandlerident = utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                ExtraUtfallEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = behandling.modified,
                    utfallIdList = behandling.extraUtfallSet.map { it.id },
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.EXTRA_UTFALL,
        )

        return ExtraUtfallEditedView(
            modified = behandling.modified,
            extraUtfallIdSet = behandling.extraUtfallSet.map { it.id }.toSet(),
        )
    }

    fun setRegistreringshjemler(
        behandlingId: UUID,
        registreringshjemler: Set<Registreringshjemmel>,
        utfoerendeSaksbehandlerIdent: String,
        systemUserContext: Boolean = false
    ): Behandling {
        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId,
            systemUserContext = systemUserContext,
        )
        //TODO: Versjonssjekk på input
        val event =
            behandling.setRegistreringshjemler(registreringshjemler, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                RegistreringshjemlerEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = if (utfoerendeSaksbehandlerIdent == systembrukerIdent) {
                            utfoerendeSaksbehandlerIdent
                        } else saksbehandlerService.getNameForIdentDefaultIfNull(
                            utfoerendeSaksbehandlerIdent
                        ),
                    ),
                    timestamp = behandling.modified,
                    hjemmelIdSet = behandling.registreringshjemler.map { it.id }.toSet(),
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.REGISTRERINGSHJEMLER,
        )

        return behandling
    }

    fun setROLFlowState(
        behandlingId: UUID,
        flowState: FlowState,
        utfoerendeSaksbehandlerIdent: String,
        systemUserContext: Boolean = false
    ): RolView {
        val behandling = getBehandlingForWriteAllowROLAndMU(
            behandlingId = behandlingId,
            utfoerendeSaksbehandlerIdent = utfoerendeSaksbehandlerIdent,
        )

        val event1 =
            behandling.setROLFlowState(
                newROLFlowStateState = flowState,
                utfoerendeIdent = utfoerendeSaksbehandlerIdent,
                utfoerendeNavn = getUtfoerendeNavn(utfoerendeSaksbehandlerIdent),
            )
        applicationEventPublisher.publishEvent(event1)

        val event2 =
            behandling.setROLReturnedDate(
                setNull = flowState != FlowState.RETURNED,
                utfoerendeIdent = utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event2)

        val rolView = behandlingMapper.mapToRolView(behandling)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                RolEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = rolView.modified,
                    rol = if (rolView.employee != null) {
                        Employee(
                            navIdent = rolView.employee.navIdent,
                            navn = rolView.employee.navn,
                        )
                    } else null,
                    flowState = rolView.flowState,
                    returnDate = behandling.rolReturnedDate,
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.ROL,
        )

        return rolView
    }

    fun setROLIdent(
        behandlingId: UUID,
        rolIdent: String?,
        utfoerendeSaksbehandlerIdent: String,
        systemUserContext: Boolean = false
    ): RolView {
        val behandlingForCheck = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        val behandling =
            if (saksbehandlerService.isKROL(utfoerendeSaksbehandlerIdent)) {
                if (behandlingForCheck.rolFlowState == FlowState.RETURNED) {
                    throw MissingTilgangException("KROL har ikke lov til å endre ROL når den er returnert.")
                }
                getBehandlingForUpdate(behandlingId = behandlingId, ignoreCheckSkrivetilgang = true)
            } else if (saksbehandlerService.hasKabalOppgavestyringAlleEnheterRole(utfoerendeSaksbehandlerIdent)) {
                if (behandlingForCheck.rolFlowState != FlowState.SENT && behandlingForCheck.tildeling?.saksbehandlerident != utfoerendeSaksbehandlerIdent) {
                    throw MissingTilgangException("OppgavestyringAlleEnheter har ikke lov til å endre ROL når den ikke er sendt.")
                }
                getBehandlingForUpdate(behandlingId = behandlingId, ignoreCheckSkrivetilgang = true)
            } else {
                if (behandlingForCheck.rolFlowState == FlowState.SENT && behandlingForCheck.rolIdent == null) {
                    if (innloggetSaksbehandlerService.isRol()) {
                        getBehandlingForUpdate(behandlingId = behandlingId, ignoreCheckSkrivetilgang = true)
                    } else {
                        getBehandlingForWriteAllowROLAndMU(
                            behandlingId = behandlingId,
                            utfoerendeSaksbehandlerIdent = utfoerendeSaksbehandlerIdent
                        )
                    }
                } else {
                    getBehandlingForWriteAllowROLAndMU(
                        behandlingId = behandlingId,
                        utfoerendeSaksbehandlerIdent = utfoerendeSaksbehandlerIdent
                    )
                }
            }

        val event =
            behandling.setROLIdent(
                newROLIdent = rolIdent,
                utfoerendeIdent = utfoerendeSaksbehandlerIdent,
                utfoerendeNavn = getUtfoerendeNavn(utfoerendeSaksbehandlerIdent),
            )
        applicationEventPublisher.publishEvent(event)

        val rolView = behandlingMapper.mapToRolView(behandling)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                RolEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = rolView.modified,
                    rol = if (rolView.employee != null) {
                        Employee(
                            navIdent = rolView.employee.navIdent,
                            navn = rolView.employee.navn,
                        )
                    } else null,
                    flowState = rolView.flowState,
                    returnDate = behandling.rolReturnedDate,
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.ROL,
        )

        return rolView
    }

    fun findCompletedBehandlingById(behandlingId: UUID): CompletedBehandling {
        val behandling = behandlingRepository.findByIdAndFerdigstillingAvsluttetIsNotNull(id = behandlingId)
        if (behandling != null) {
            checkLesetilgangForPerson(behandling)
            return behandling.toCompletedBehandling()
        } else {
            throw BehandlingNotFoundException("Completed behandling with id $behandlingId not found")
        }
    }

    private fun Behandling.toCompletedBehandling(): CompletedBehandling = CompletedBehandling(
        behandlingId = id,
        ytelseId = ytelse.id,
        hjemmelIdList = hjemler.map { it.id },
        vedtakDate = ferdigstilling!!.avsluttetAvSaksbehandler,
        sakenGjelder = behandlingMapper.getSakenGjelderViewWithUtsendingskanal(behandling = this).toKabinPartView(),
        klager = behandlingMapper.getPartViewWithUtsendingskanal(
            technicalPartId = klager.id,
            partId = klager.partId,
            behandling = this,
            navn = null,
            address = null,
        ).toKabinPartView(),
        fullmektig = if (prosessfullmektig?.partId != null) {
            behandlingMapper.getPartViewWithUtsendingskanal(
                technicalPartId = prosessfullmektig!!.id,
                partId = prosessfullmektig!!.partId,
                behandling = this,
                navn = prosessfullmektig!!.navn,
                address = prosessfullmektig!!.address,
            ).toKabinPartView()
        } else null,
        fagsakId = fagsakId,
        fagsystem = fagsystem,
        fagsystemId = fagsystem.id,
        klageBehandlendeEnhet = tildeling!!.enhet!!,
        tildeltSaksbehandlerIdent = tildeling!!.saksbehandlerident!!,
        tildeltSaksbehandlerNavn = saksbehandlerService.getNameForIdentDefaultIfNull(tildeling!!.saksbehandlerident!!),
    )

    fun getHistory(behandlingId: UUID): HistoryResponse {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId = behandlingId)

        return HistoryResponse(
            tildeling = historyService.createTildelingHistory(
                tildelingHistorikkSet = behandling.tildelingHistorikk,
                behandlingCreated = behandling.created,
                originalHjemmelIdList = behandling.hjemler.joinToString(",")
            ),
            medunderskriver = historyService.createMedunderskriverHistory(
                medunderskriverHistorikkSet = behandling.medunderskriverHistorikk,
                behandlingCreated = behandling.created,
            ),
            rol = historyService.createRolHistory(
                rolHistorikk = behandling.rolHistorikk,
            ),
            klager = historyService.createKlagerHistory(
                klagerHistorikk = behandling.klagerHistorikk,
            ),
            fullmektig = historyService.createFullmektigHistory(
                fullmektigHistorikk = behandling.fullmektigHistorikk,
            ),
            sattPaaVent = historyService.createSattPaaVentHistory(
                sattPaaVentHistorikk = behandling.sattPaaVentHistorikk,
            ),
            ferdigstilt = historyService.createFerdigstiltHistory(behandling),
            feilregistrert = historyService.createFeilregistrertHistory(
                feilregistrering = behandling.feilregistrering,
                behandlingCreated = behandling.created,
            ),
            varsletBehandlingstid = historyService.createVarsletBehandlingstidHistory(
                varsletBehandlingstidHistorikk = behandling.varsletBehandlingstidHistorikk,
                behandlingCreated = behandling.created,
            ),
            forlengetBehandlingstid = historyService.createForlengetBehandlingstidHistory(
                varsletBehandlingstidHistorikk = behandling.varsletBehandlingstidHistorikk,
                behandlingCreated = behandling.created,
            ),
        )
    }

    fun findRelevantBehandlinger(behandlingId: UUID): List<Behandling> {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        return behandlingRepository.findBySakenGjelderPartIdValueAndFerdigstillingIsNullAndFeilregistreringIsNull(
            partIdValue = behandling.sakenGjelder.partId.value
        )
    }

    fun findRelevantGosysOppgaver(behandlingId: UUID): List<GosysOppgaveView> {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)
        return gosysOppgaveService.getGosysOppgaveList(
            fnr = behandling.sakenGjelder.partId.value,
            tema = behandling.ytelse.toTema(),
        ).map {
            it.copy(
                alreadyUsedBy = findOpenBehandlingUsingGosysOppgave(it.id)
            )
        }
    }

    fun getSakenGjelderView(behandlingId: UUID): BehandlingDetaljerView.SakenGjelderView {
        return behandlingMapper.getSakenGjelderView(getBehandlingAndCheckLeseTilgangForPerson(behandlingId).sakenGjelder)
    }

    fun getFradelingReason(behandlingId: UUID): WithPrevious<no.nav.klage.oppgave.api.view.TildelingEvent>? {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId)

        val tildelingHistory = historyService.createTildelingHistory(
            tildelingHistorikkSet = behandling.tildelingHistorikk,
            behandlingCreated = behandling.created,
            originalHjemmelIdList = behandling.hjemler.joinToString(",")
        )

        return if (behandling.tildeling == null) {
            val fradelingerBySaksbehandler = tildelingHistory.filter {
                it.event?.fradelingReasonId != null && it.previous.event?.saksbehandler?.navIdent == innloggetSaksbehandlerService.getInnloggetIdent()
            }

            if (fradelingerBySaksbehandler.isNotEmpty()) {
                //return most recent fradeling if there are multiple
                fradelingerBySaksbehandler.last()
            } else {
                null
            }
        } else null
    }

    fun getFradeltSaksbehandlerViewWrapped(behandlingId: UUID): FradeltSaksbehandlerViewWrapped {
        val behandling = getBehandlingForReadWithoutCheckForAccess(behandlingId)
        return FradeltSaksbehandlerViewWrapped(
            modified = behandling.modified,
            hjemmelIdList = behandling.hjemler.map { it.id }
        )
    }

    fun gosysOppgaveIsDuplicate(gosysOppgaveId: Long): Boolean {
        return behandlingRepository.findByGosysOppgaveIdAndFeilregistreringIsNullAndFerdigstillingIsNull(
            gosysOppgaveId = gosysOppgaveId
        ).isNotEmpty()
    }

    fun findOpenBehandlingUsingGosysOppgave(gosysOppgaveId: Long): UUID? {
        val behandlingList = behandlingRepository.findByGosysOppgaveIdAndFeilregistreringIsNullAndFerdigstillingIsNull(
            gosysOppgaveId = gosysOppgaveId
        )

        return if (behandlingList.isEmpty()) {
            null
        } else if (behandlingList.size != 1) {
            throw RuntimeException("Found more than one behandling for gosysOppgaveId $gosysOppgaveId, investigate")
        } else {
            behandlingList.first().id
        }
    }

    fun getAnkemuligheterByPartIdValue(
        partIdValue: String,
    ): List<Behandling> {
        return behandlingRepository.getAnkemuligheter(partIdValue)
    }

    fun getOmgjoeringskravmuligheterByPartIdValue(
        partIdValue: String,
    ): List<Behandling> {
        return behandlingRepository.getOmgjoeringskravmuligheter(partIdValue)
    }

    private fun getUtfoerendeNavn(utfoerendeSaksbehandlerIdent: String): String {
        val name = if (utfoerendeSaksbehandlerIdent == systembrukerIdent) {
            systembrukerIdent
        } else saksbehandlerService.getNameForIdentDefaultIfNull(
            navIdent = utfoerendeSaksbehandlerIdent
        )
        return name
    }

    fun setGosysOppgaveId(
        behandlingId: UUID,
        gosysOppgaveId: Long,
        utfoerendeSaksbehandlerIdent: String,
    ): GosysOppgaveEditedView {
        logger.debug("Input utfall in setGosysOppgaveId: {}", gosysOppgaveId)

        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId,
        )

        val gosysOppgave = gosysOppgaveService.getGosysOppgave(
            gosysOppgaveId = gosysOppgaveId,
            fnrToValidate = behandling.sakenGjelder.partId.value,
        ).copy(
            alreadyUsedBy = findOpenBehandlingUsingGosysOppgave(gosysOppgaveId)
        )

        if (behandling.gosysOppgaveId == gosysOppgave.id) {
            return GosysOppgaveEditedView(
                modified = behandling.modified,
                gosysOppgave = gosysOppgave
            )
        }

        if (gosysOppgave.alreadyUsedBy != null) {
            throw DuplicateGosysOppgaveIdException("Gosysoppgave med id $gosysOppgaveId er allerede i bruk")
        }

        val event =
            behandling.setGosysOppgaveId(
                nyVerdi = gosysOppgaveId,
                saksbehandlerident = utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)

        publishInternalEvent(
            data = objectMapper.writeValueAsString(
                GosysoppgaveEvent(
                    actor = Employee(
                        navIdent = utfoerendeSaksbehandlerIdent,
                        navn = saksbehandlerService.getNameForIdentDefaultIfNull(utfoerendeSaksbehandlerIdent),
                    ),
                    timestamp = behandling.modified,
                    gosysOppgave = gosysOppgave,
                )
            ),
            behandlingId = behandlingId,
            type = InternalEventType.GOSYSOPPGAVE,
        )

        return GosysOppgaveEditedView(
            modified = behandling.modified,
            gosysOppgave = gosysOppgave,
        )
    }

    fun getGosysOppgave(behandlingId: UUID): GosysOppgaveView {
        val behandling = getBehandlingAndCheckLeseTilgangForPerson(behandlingId = behandlingId)

        if (behandling.gosysOppgaveId == null) {
            throw GosysOppgaveNotFoundException("Behandlingen har ingen gosysoppgave")
        }

        val gosysOppgave = gosysOppgaveService.getGosysOppgave(
            gosysOppgaveId = behandling.gosysOppgaveId!!,
            fnrToValidate = behandling.sakenGjelder.partId.value
        )

        return gosysOppgave.copy(
            alreadyUsedBy = findOpenBehandlingUsingGosysOppgave(gosysOppgave.id)
        )
    }
}
