package no.nav.klage.oppgave.service

import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.kodeverk.MedunderskriverFlyt
import no.nav.klage.kodeverk.Tema
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.oppgave.api.view.DokumenterResponse
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.klage.Ankebehandling
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.addSaksdokument
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.removeSaksdokument
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setAvsluttetAvSaksbehandler
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setMedunderskriverFlyt
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setMedunderskriverIdentAndMedunderskriverFlyt
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setMottattKlageinstans
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setSattPaaVent
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setTildeling
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setMottattVedtaksinstans
import no.nav.klage.oppgave.domain.klage.Saksdokument
import no.nav.klage.oppgave.exceptions.*
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
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
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }


    fun ferdigstillBehandling(
        behandlingId: UUID,
        innloggetIdent: String
    ): Behandling {
        val behandling = getBehandlingForUpdate(
            behandlingId = behandlingId
        )

        if (behandling.currentDelbehandling().avsluttetAvSaksbehandler != null) throw BehandlingFinalizedException("Behandlingen er avsluttet")

        //Forretningsmessige krav før vedtak kan ferdigstilles
        validateBehandlingBeforeFinalize(behandling)

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

    fun validateBehandlingBeforeFinalize(behandling: Behandling) {
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

        if (behandling.currentDelbehandling().utfall == null) {
            behandlingValidationErrors.add(
                InvalidProperty(
                    field = "utfall",
                    reason = "Sett et utfall på vedtaket."
                )
            )
        }
        if (behandling.currentDelbehandling().utfall != Utfall.TRUKKET) {
            if (behandling.currentDelbehandling().hjemler.isEmpty()) {
                behandlingValidationErrors.add(
                    InvalidProperty(
                        field = "hjemmel",
                        reason = "Sett en eller flere hjemler på vedtaket."
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

        if (behandlingValidationErrors.isNotEmpty()) {
            sectionList.add(
                ValidationSection(
                    section = "behandling",
                    properties = behandlingValidationErrors
                )
            )
        }

        val kvalitetsvurderingValidationErrors = kakaApiGateway.getValidationErrors(behandling)

        if (kvalitetsvurderingValidationErrors.isNotEmpty()) {
            sectionList.add(
                ValidationSection(
                    section = "kvalitetsvurdering",
                    properties = kvalitetsvurderingValidationErrors
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

    fun assignBehandling(
        behandlingId: UUID,
        tildeltSaksbehandlerIdent: String?,
        enhetId: String?,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
        val behandling = getBehandlingForUpdate(behandlingId, true)
        if (tildeltSaksbehandlerIdent != null) {
            //Denne sjekken gjøres kun når det er en tildeling:
            checkEnhetOgTemaTilgang(tildeltSaksbehandlerIdent, enhetId!!, behandling)
        }
        val event =
            behandling.setTildeling(
                tildeltSaksbehandlerIdent,
                enhetId,
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return behandling
    }

    fun setSattPaaVent(
        behandlingId: UUID,
        setNull: Boolean = false,
        utfoerendeSaksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(behandlingId, true)
        val nyVerdi = if (setNull) null else LocalDateTime.now()
        val event =
            behandling.setSattPaaVent(
                nyVerdi,
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return behandling.modified
    }

    fun setMottattKlageinstans(
        behandlingId: UUID,
        date: LocalDateTime,
        utfoerendeSaksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(
            behandlingId
        )

        if (behandling is Ankebehandling) {
            val event =
                behandling.setMottattKlageinstans(date, utfoerendeSaksbehandlerIdent)
            applicationEventPublisher.publishEvent(event)
            return behandling.modified
        } else throw IllegalOperation("Dette feltet kan bare settes i ankesaker")
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

    fun setMedunderskriverIdentAndMedunderskriverFlyt(
        behandlingId: UUID,
        medunderskriverIdent: String?,
        utfoerendeSaksbehandlerIdent: String,
        medunderskriverFlyt: MedunderskriverFlyt = MedunderskriverFlyt.IKKE_SENDT
    ): Behandling {
        val behandling = getBehandlingForUpdate(behandlingId)
        val event =
            behandling.setMedunderskriverIdentAndMedunderskriverFlyt(
                medunderskriverIdent,
                medunderskriverFlyt,
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return behandling
    }

    fun switchMedunderskriverFlyt(
        behandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
        val behandling = getBehandling(behandlingId)

        if (behandling.currentDelbehandling().medunderskriver?.saksbehandlerident == null) {
            throw BehandlingManglerMedunderskriverException("Behandlingen har ikke registrert noen medunderskriver")
        }

        if (behandling.currentDelbehandling().medunderskriver?.saksbehandlerident == utfoerendeSaksbehandlerIdent) {
            checkMedunderskriverStatus(behandling)
            if (behandling.currentDelbehandling().medunderskriverFlyt != MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER) {
                val event = behandling.setMedunderskriverFlyt(
                    MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER,
                    utfoerendeSaksbehandlerIdent
                )
                applicationEventPublisher.publishEvent(event)
            }
        } else {
            checkSkrivetilgang(behandling)
            if (behandling.currentDelbehandling().medunderskriverFlyt != MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER) {
                val event = behandling.setMedunderskriverFlyt(
                    MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER,
                    utfoerendeSaksbehandlerIdent
                )
                applicationEventPublisher.publishEvent(event)
            }
        }

        return behandling
    }

    //TODO Quick fix to make sure all old klagebehandlinger get kakaKvalitetsvurderingId
    fun createAndStoreKakaKvalitetsvurderingIdIfMissing(behandlingId: UUID) {
        logger.debug("Checking if behandling contains kvalitetsvurdering from Kaka.")
        val behandling = behandlingRepository.findById(behandlingId)
            .orElseThrow { BehandlingNotFoundException("Behandling med id $behandlingId ikke funnet") }

        if (behandling.kakaKvalitetsvurderingId == null) {
            logger.debug("Klagebehandling did not contain a kvalitetsvurdering from Kaka, so will create it.")
            behandling.kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering()
        }
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

    fun fetchJournalposterConnectedToBehandling(behandlingId: UUID): DokumenterResponse {
        val behandling = getBehandling(behandlingId)
        return dokumentService.fetchJournalposterConnectedToBehandling(behandling)
    }

    fun connectDokumentToBehandling(
        behandlingId: UUID,
        journalpostId: String,
        dokumentInfoId: String,
        saksbehandlerIdent: String
    ): LocalDateTime {
        val behandling = getBehandlingForUpdate(behandlingId)
        dokumentService.validateJournalpostExists(journalpostId)
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
        ignoreCheckSkrivetilgang: Boolean = false
    ): Behandling =
        behandlingRepository.findById(behandlingId).get()
            .also { checkLeseTilgang(it) }
            .also { if (!ignoreCheckSkrivetilgang) checkSkrivetilgang(it) }

    private fun checkLeseTilgang(behandling: Behandling) {
        if (behandling.sakenGjelder.erPerson()) {
            tilgangService.verifyInnloggetSaksbehandlersTilgangTil(behandling.sakenGjelder.partId.value)
        }
        tilgangService.verifyInnloggetSaksbehandlersTilgangTilYtelse(behandling.ytelse)
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
        behandlingRepository.getById(behandlingId)
            .also { checkSkrivetilgangForSystembruker(it) }

    private fun checkEnhetOgTemaTilgang(
        tildeltSaksbehandlerIdent: String,
        tildeltEnhetId: String,
        behandling: Behandling
    ) {
        tilgangService.verifySaksbehandlersTilgangTilEnhetOgYtelse(
            tildeltSaksbehandlerIdent,
            tildeltEnhetId,
            behandling.ytelse
        )
    }

    private fun checkMedunderskriverStatus(behandling: Behandling) {
        tilgangService.verifyInnloggetSaksbehandlerErMedunderskriverAndNotFinalized(behandling)
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
                logger.debug("Dokument (journalpost: $journalpostId dokumentInfoId: $dokumentInfoId) is already connected to behandling ${behandling.id}, doing nothing")
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
    fun getBehandlingForSmartEditor(behandlingId: UUID, utfoerendeSaksbehandlerIdent: String): Behandling {
        val behandling = behandlingRepository.findById(behandlingId).get()
        if (behandling.currentDelbehandling().medunderskriver?.saksbehandlerident == utfoerendeSaksbehandlerIdent) {
            checkMedunderskriverStatus(behandling)
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
    fun findBehandlingerForAvslutning(): List<UUID> =
        behandlingRepository.findByDelbehandlingerAvsluttetIsNullAndDelbehandlingerAvsluttetAvSaksbehandlerIsNotNull()
            .map { it.id }

}