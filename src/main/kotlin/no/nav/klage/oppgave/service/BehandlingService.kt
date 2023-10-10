package no.nav.klage.oppgave.service

import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.mapper.BehandlingMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.clients.arbeidoginntekt.ArbeidOgInntektClient
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Medunderskrivere
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Saksbehandlere
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.HandledInKabalInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakAssignedInput
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandlingSetters.setKjennelseMottatt
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandlingSetters.setNyAnkebehandlingKA
import no.nav.klage.oppgave.domain.klage.AnkeITrygderettenbehandlingSetters.setSendtTilTrygderetten
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.addSaksdokument
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.removeSaksdokument
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setAvsluttetAvSaksbehandler
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setExtraUtfallSet
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setFeilregistrering
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setFrist
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setFullmektig
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
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setTildeling
import no.nav.klage.oppgave.domain.klage.BehandlingSetters.setUtfall
import no.nav.klage.oppgave.domain.klage.KlagebehandlingSetters.setMottattVedtaksinstans
import no.nav.klage.oppgave.exceptions.*
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.repositories.SaksbehandlerRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getPartIdFromIdentifikator
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
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val eregClient: EregClient,
    private val saksbehandlerService: SaksbehandlerService,
    private val behandlingMapper: BehandlingMapper,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }


    fun ferdigstillBehandling(
        behandlingId: UUID,
        innloggetIdent: String,
        nyBehandling: Boolean
    ): Behandling {
        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId
        )

        if (behandling.avsluttetAvSaksbehandler != null) throw BehandlingFinalizedException("Behandlingen er avsluttet")

        //Forretningsmessige krav før vedtak kan ferdigstilles
        validateBehandlingBeforeFinalize(behandlingId = behandlingId, nyBehandling = nyBehandling)

        if (nyBehandling) {
            return setNyAnkebehandlingKA(behandlingId, innloggetIdent)
        }

        //Her settes en markør som så brukes async i kallet klagebehandlingRepository.findByAvsluttetIsNullAndAvsluttetAvSaksbehandlerIsNotNull
        return markerBehandlingSomAvsluttetAvSaksbehandler(behandling, innloggetIdent)
    }

    private fun markerBehandlingSomAvsluttetAvSaksbehandler(
        behandling: Behandling,
        innloggetIdent: String
    ): Behandling {
        val event = behandling.setAvsluttetAvSaksbehandler(innloggetIdent)
        applicationEventPublisher.publishEvent(event)
        return behandling
    }

    fun validateBehandlingBeforeFinalize(behandlingId: UUID, nyBehandling: Boolean) {
        val behandling = getBehandling(behandlingId)
        val dokumentValidationErrors = mutableListOf<InvalidProperty>()
        val behandlingValidationErrors = mutableListOf<InvalidProperty>()
        val sectionList = mutableListOf<ValidationSection>()

        if (nyBehandling) {
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
                    reason = "Sett et utfall på vedtaket."
                )
            )
        }

        //TODO: Create test for invalid utfall when such are added
        if (behandling.utfall != null && behandling.utfall !in typeTilUtfall[behandling.type]!!) {
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
                        reason = "Sett en eller flere hjemler på vedtaket."
                    )
                )
            }
        }

        if (behandling !is AnkeITrygderettenbehandling && behandling.utfall !in noKvalitetsvurderingNeeded) {
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

        if (behandling.klager.prosessfullmektig?.isPerson() == false &&
            !eregClient.hentOrganisasjon(behandling.klager.prosessfullmektig!!.partId.value).isActive()
        ) {
            behandlingValidationErrors.add(
                InvalidProperty(
                    field = "fullmektig",
                    reason = "Fullmektig/organisasjon har opphørt."
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
        val behandling = getBehandling(behandlingId) as AnkeITrygderettenbehandling
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
        val behandling = getBehandling(behandlingId)
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

    fun setSaksbehandler(
        behandlingId: UUID,
        tildeltSaksbehandlerIdent: String?,
        enhetId: String?,
        utfoerendeSaksbehandlerIdent: String,
    ): SaksbehandlerViewWrapped {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId, ignoreCheckSkrivetilgang = true)
        if (tildeltSaksbehandlerIdent != null) {
            //Denne sjekken gjøres kun når det er en tildeling:

            checkYtelseAccess(tildeltSaksbehandlerIdent = tildeltSaksbehandlerIdent, behandling = behandling)

            //if fagsystem is Infotrygd also do this.
            if (behandling.fagsystem == Fagsystem.IT01) {
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
        } else {
            if (behandling.medunderskriverFlowState == FlowState.SENT) {
                throw IllegalOperation("Kan ikke fradele behandling sendt til medunderskriver.")
            }

            //if fagsystem is Infotrygd also do this.
            if (behandling.fagsystem == Fagsystem.IT01 && behandling.type != Type.ANKE_I_TRYGDERETTEN) {
                logger.debug("Fradeling av behandling skal registreres i Infotrygd.")
                fssProxyClient.setToHandledInKabal(
                    sakId = behandling.kildeReferanse,
                    input = HandledInKabalInput(
                        fristAsString = behandling.frist!!.format(DateTimeFormatter.BASIC_ISO_DATE),
                    )
                )
                logger.debug("Fradeling av behandling ble registrert i Infotrygd.")
            }

            //Fjern på vent-status
            setSattPaaVent(
                behandlingId = behandlingId,
                utfoerendeSaksbehandlerIdent = utfoerendeSaksbehandlerIdent,
                systemUserContext = saksbehandlerRepository.hasKabalOppgavestyringAlleEnheterRole(
                    utfoerendeSaksbehandlerIdent
                ),
                input = null,
            )
        }

        val event =
            behandling.setTildeling(
                nyVerdiSaksbehandlerident = tildeltSaksbehandlerIdent,
                nyVerdiEnhet = enhetId,
                saksbehandlerident = utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return getSaksbehandlerViewWrapped(behandling)
    }

    fun getSaksbehandler(behandlingId: UUID): SaksbehandlerViewWrapped {
        return getSaksbehandlerViewWrapped(getBehandling((behandlingId)))
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
                navn = saksbehandlerService.getNameForIdent(behandling.tildeling?.saksbehandlerident!!),
            )
        }
        return saksbehandlerView
    }

    fun getMedunderskriver(behandlingId: UUID): MedunderskriverWrapped {
        val behandling = getBehandling(behandlingId)
        return behandlingMapper.mapToMedunderskriverWrapped(behandling)
    }

    fun getMedunderskriverFlowState(behandlingId: UUID): FlowStateView {
        val behandling = getBehandling(behandlingId)
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
                reason = input.reason
            )
        } else null

        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId,
            systemUserContext = systemUserContext
        )
        val event =
            behandling.setSattPaaVent(
                sattPaaVent,
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
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

    fun setNyAnkebehandlingKA(
        behandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
        val behandling = getBehandlingForUpdate(behandlingId = behandlingId)

        if (behandling is AnkeITrygderettenbehandling) {
            val eventNyBehandling = behandling.setNyAnkebehandlingKA(LocalDateTime.now(), utfoerendeSaksbehandlerIdent)
            val eventAvsluttetAvSaksbehandler = behandling.setAvsluttetAvSaksbehandler(utfoerendeSaksbehandlerIdent)
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
            behandlingId
        )

        val event =
            behandling.setInnsendingshjemler(
                hjemler.map { Hjemmel.of(it) }.toSet(),
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return behandling.modified
    }

    fun setFullmektig(
        behandlingId: UUID,
        identifikator: String?,
        utfoerendeSaksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(
            behandlingId
        )

        val partId: PartId? = if (identifikator == null) {
            null
        } else {
            getPartIdFromIdentifikator(identifikator)
        }

        val event =
            behandling.setFullmektig(
                partId,
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
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

        val event =
            behandling.setKlager(
                nyVerdi = getPartIdFromIdentifikator(identifikator),
                saksbehandlerident = utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
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
                saksbehandlerident = utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return behandlingMapper.mapToMedunderskriverWrapped(behandling)
    }

    fun setMedunderskriverNavIdent(
        behandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String,
        navIdent: String?,
    ): MedunderskriverWrapped {
        val behandling =
            if (saksbehandlerRepository.hasKabalOppgavestyringAlleEnheterRole(utfoerendeSaksbehandlerIdent)) {
                val behandling = getBehandling(behandlingId)
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

        val event =
            behandling.setMedunderskriverNavIdent(
                nyMedunderskriverNavIdent = navIdent,
                saksbehandlerident = utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return behandlingMapper.mapToMedunderskriverWrapped(behandling)
    }

    fun fetchDokumentlisteForBehandling(
        behandlingId: UUID,
        temaer: List<Tema>,
        pageSize: Int,
        previousPageRef: String?
    ): DokumenterResponse {
        val behandling = getBehandling(behandlingId)
        return dokumentService.fetchDokumentlisteForBehandling(behandling, temaer, pageSize, previousPageRef)
    }

    fun connectDokumentToBehandling(
        behandlingId: UUID,
        journalpostId: String,
        dokumentInfoId: String,
        saksbehandlerIdent: String,
        systemUserContext: Boolean = false,
        ignoreCheckSkrivetilgang: Boolean,
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId,
            ignoreCheckSkrivetilgang = ignoreCheckSkrivetilgang,
            systemUserContext = systemUserContext,
        )
        if (systemUserContext) {
            dokumentService.validateJournalpostExistsAsSystembruker(journalpostId)
        } else {
            dokumentService.validateJournalpostExists(journalpostId)
        }

        addDokument(
            behandling,
            journalpostId,
            dokumentInfoId,
            saksbehandlerIdent
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
            .also { if (!systemUserContext) checkLeseTilgang(it) }
            .also { if (!systemUserContext && !ignoreCheckSkrivetilgang) checkSkrivetilgang(it) }

    fun checkLeseTilgang(behandling: Behandling) {
        if (behandling.sakenGjelder.erPerson()) {
            checkLeseTilgang(behandling.sakenGjelder.partId.value)
        }
    }

    fun checkLeseTilgang(partIdValue: String) {
        tilgangService.verifyInnloggetSaksbehandlersTilgangTil(partIdValue)
    }

    private fun checkSkrivetilgang(behandling: Behandling) {
        tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(behandling)
    }

    private fun checkSkrivetilgangForSystembruker(behandling: Behandling) {
        tilgangService.verifySystembrukersSkrivetilgang(behandling)
    }

    @Transactional(readOnly = true)
    fun getBehandlingForReadWithoutCheckForAccess(behandlingId: UUID): Behandling =
        behandlingRepository.findById(behandlingId)
            .orElseThrow { BehandlingNotFoundException("Behandling med id $behandlingId ikke funnet") }

    fun getBehandlingForUpdateBySystembruker(
        behandlingId: UUID,
    ): Behandling =
        behandlingRepository.getReferenceById(behandlingId)
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

    private fun addDokument(
        behandling: Behandling,
        journalpostId: String,
        dokumentInfoId: String,
        saksbehandlerIdent: String
    ) {
        try {
            val foundSaksdokument =
                behandling.saksdokumenter.find { it.journalpostId == journalpostId && it.dokumentInfoId == dokumentInfoId }
            if (foundSaksdokument != null) {
                logger.debug(
                    "Dokument (journalpost: {} dokumentInfoId: {}) is already connected to behandling {}, doing nothing",
                    journalpostId,
                    dokumentInfoId,
                    behandling.id
                )
            } else {
                val saksdokument = Saksdokument(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId
                )
                val event = behandling.addSaksdokument(
                    saksdokument,
                    saksbehandlerIdent
                )
                event?.let { applicationEventPublisher.publishEvent(it) }
            }
        } catch (e: Exception) {
            logger.error("Error connecting journalpost $journalpostId to behandling ${behandling.id}", e)
            throw e
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

    @Transactional(readOnly = true)
    fun getBehandling(behandlingId: UUID): Behandling =
        behandlingRepository.findById(behandlingId)
            .orElseThrow { BehandlingNotFoundException("Behandling med id $behandlingId ikke funnet") }
            .also { checkLeseTilgang(it) }

    @Transactional(readOnly = true)
    fun findBehandlingerForAvslutning(): List<Pair<UUID, Type>> =
        behandlingRepository.findByAvsluttetIsNullAndAvsluttetAvSaksbehandlerIsNotNullAndFeilregistreringIsNull()
            .map { it.id to it.type }

    fun getPotentialSaksbehandlereForBehandling(behandlingId: UUID): Saksbehandlere {
        val behandling = getBehandling(behandlingId)
        return kabalInnstillingerService.getPotentialSaksbehandlere(behandling)
    }

    fun getPotentialMedunderskrivereForBehandling(behandlingId: UUID): Medunderskrivere {
        val behandling = getBehandling(behandlingId)
        return kabalInnstillingerService.getPotentialMedunderskrivere(behandling)
    }

    fun getPotentialROLForBehandling(behandlingId: UUID): Rols {
        val behandling = getBehandling(behandlingId)
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
        return behandlingRepository.findByTildelingEnhetAndAvsluttetAvSaksbehandlerIsNullAndFeilregistreringIsNull(
            enhet
        )
    }

    fun getAInntektUrl(behandlingId: UUID): String {
        val behandling = getBehandling(behandlingId = behandlingId)
        return arbeidOgInntektClient.getAInntektUrl(behandling.sakenGjelder.partId.value)
    }

    fun getAARegisterUrl(behandlingId: UUID): String {
        val behandling = getBehandling(behandlingId = behandlingId)
        return arbeidOgInntektClient.getAARegisterUrl(behandling.sakenGjelder.partId.value)
    }

    fun feilregistrer(behandlingId: UUID, reason: String, fagsystem: Fagsystem): FeilregistreringResponse {
        val navIdent = innloggetSaksbehandlerService.getInnloggetIdent()
        val behandlingForCheck = getBehandling(behandlingId)

        val behandling =
            if (saksbehandlerRepository.hasKabalOppgavestyringAlleEnheterRole(navIdent) || behandlingForCheck.tildeling == null) {
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
                    navn = saksbehandlerService.getNameForIdent(modifiedBehandling.feilregistrering!!.navIdent)
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

        candidates = candidates.filter { it.avsluttetAvSaksbehandler == null }

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
            fagsystem = fagsystem
        )
    }

    private fun feilregistrer(
        behandling: Behandling,
        navIdent: String,
        reason: String,
        fagsystem: Fagsystem
    ): Behandling {
        val event = behandling.setFeilregistrering(
            feilregistrering = Feilregistrering(
                navIdent = navIdent,
                registered = LocalDateTime.now(),
                reason = reason,
                fagsystem = fagsystem,
            ),
            saksbehandlerident = navIdent,
        )
        applicationEventPublisher.publishEvent(event)
        return behandling
    }

    fun setUtfall(
        behandlingId: UUID,
        utfall: Utfall?,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
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

        return behandling
    }

    fun setExtraUtfallSet(
        behandlingId: UUID,
        extraUtfallSet: Set<Utfall>,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
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
        return behandling
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
        return behandling
    }

    fun setROLFlowState(
        behandlingId: UUID,
        flowState: FlowState,
        utfoerendeSaksbehandlerIdent: String,
        systemUserContext: Boolean = false
    ): Behandling {
        val behandling = getBehandlingForWriteAllowROLAndMU(
            behandlingId = behandlingId,
            utfoerendeSaksbehandlerIdent = utfoerendeSaksbehandlerIdent,
        )

        val event1 =
            behandling.setROLFlowState(
                newROLFlowStateState = flowState,
                saksbehandlerident = utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event1)

        val event2 =
            behandling.setROLReturnedDate(
                setNull = flowState != FlowState.RETURNED,
                saksbehandlerident = utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event2)

        return behandling
    }

    fun setROLIdent(
        behandlingId: UUID,
        rolIdent: String?,
        utfoerendeSaksbehandlerIdent: String,
        systemUserContext: Boolean = false
    ): Behandling {
        val behandlingForCheck = getBehandling(behandlingId)
        val behandling =
            if (saksbehandlerRepository.hasKabalOppgavestyringAlleEnheterRole(utfoerendeSaksbehandlerIdent)) {
                if (behandlingForCheck.rolFlowState != FlowState.SENT && behandlingForCheck.tildeling?.saksbehandlerident != utfoerendeSaksbehandlerIdent) {
                    throw MissingTilgangException("OppgavestyringAlleEnheter har ikke lov til å endre ROL når den ikke er sent.")
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
                saksbehandlerident = utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return behandling
    }
}