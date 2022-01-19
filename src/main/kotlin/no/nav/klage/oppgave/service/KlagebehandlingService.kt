package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.MedunderskriverFlyt
import no.nav.klage.kodeverk.Tema
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.api.view.DokumenterResponse
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.addSaksdokument
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.removeSaksdokument
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setAvsluttetAvSaksbehandler
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setMedunderskriverFlyt
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setMedunderskriverIdentAndMedunderskriverFlyt
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setTildeling
import no.nav.klage.oppgave.exceptions.*
import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class KlagebehandlingService(
    private val klagebehandlingRepository: KlagebehandlingRepository,
    private val tilgangService: TilgangService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val dokumentService: DokumentService,
    private val tokenUtil: TokenUtil,
    private val kabalDocumentGateway: KabalDocumentGateway,
    private val kakaApiGateway: KakaApiGateway,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    private fun checkLeseTilgang(klagebehandling: Klagebehandling) {
        if (klagebehandling.sakenGjelder.erPerson()) {
            tilgangService.verifyInnloggetSaksbehandlersTilgangTil(klagebehandling.sakenGjelder.partId.value)
        }
        tilgangService.verifyInnloggetSaksbehandlersTilgangTilYtelse(klagebehandling.ytelse)
    }

    private fun checkMedunderskriverStatus(klagebehandling: Klagebehandling) {
        tilgangService.verifyInnloggetSaksbehandlerErMedunderskriver(klagebehandling)
    }

    private fun checkSkrivetilgang(klagebehandling: Klagebehandling) {
        tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling)
    }

    private fun checkSkrivetilgangForSystembruker(klagebehandling: Klagebehandling) {
        tilgangService.verifySystembrukersSkrivetilgang(klagebehandling)
    }

    private fun checkEnhetOgTemaTilgang(
        tildeltSaksbehandlerIdent: String,
        tildeltEnhetId: String,
        klagebehandling: Klagebehandling
    ) {
        tilgangService.verifySaksbehandlersTilgangTilEnhetOgYtelse(
            tildeltSaksbehandlerIdent,
            tildeltEnhetId,
            klagebehandling.ytelse
        )
    }

    //TODO Quick fix to make sure all old klagebehandlinger get kakaKvalitetsvurderingId
    fun createAndStoreKakaKvalitetsvurderingIdIfMissing(klagebehandlingId: UUID) {
        logger.debug("Checking if Klagebehandling contains kvalitetsvurdering from Kaka.")
        val klagebehandling = klagebehandlingRepository.findById(klagebehandlingId)
            .orElseThrow { KlagebehandlingNotFoundException("Klagebehandling med id $klagebehandlingId ikke funnet") }

        if (klagebehandling.kakaKvalitetsvurderingId == null) {
            logger.debug("Klagebehandling did not contain a kvalitetsvurdering from Kaka, so will create it.")
            klagebehandling.kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering()
        }
    }

    @Transactional(readOnly = true)
    fun getKlagebehandling(klagebehandlingId: UUID): Klagebehandling =
        klagebehandlingRepository.findById(klagebehandlingId)
            .orElseThrow { KlagebehandlingNotFoundException("Klagebehandling med id $klagebehandlingId ikke funnet") }
            .also { checkLeseTilgang(it) }

    @Transactional(readOnly = true)
    fun getKlagebehandlingForReadWithoutCheckForAccess(klagebehandlingId: UUID): Klagebehandling =
        klagebehandlingRepository.findById(klagebehandlingId)
            .orElseThrow { KlagebehandlingNotFoundException("Klagebehandling med id $klagebehandlingId ikke funnet") }

    fun getKlagebehandlingForUpdate(
        klagebehandlingId: UUID,
        ignoreCheckSkrivetilgang: Boolean = false
    ): Klagebehandling =
        klagebehandlingRepository.getOne(klagebehandlingId)
            .also { checkLeseTilgang(it) }
            .also { if (!ignoreCheckSkrivetilgang) checkSkrivetilgang(it) }

    fun getKlagebehandlingForUpdateBySystembruker(
        klagebehandlingId: UUID,
    ): Klagebehandling =
        klagebehandlingRepository.getOne(klagebehandlingId)
            .also { checkSkrivetilgangForSystembruker(it) }

    var muligAnkeUtfall = setOf(
        Utfall.MEDHOLD,
        Utfall.DELVIS_MEDHOLD,
        Utfall.STADFESTELSE,
        Utfall.UGUNST,
        Utfall.AVVIST
    )

    fun findMuligAnkeByPartId(
        partId: String
    ): List<MuligAnke> =
        klagebehandlingRepository.findByAvsluttetIsNotNull()
            .filter {
                it.klager.partId.value == partId &&
                        muligAnkeUtfall.contains(it.currentDelbehandling().utfall)
            }
            .map { it.toMuligAnke() }

    fun findMuligAnkeByPartIdAndKlagebehandlingId(
        partId: String,
        klagebehandlingId: UUID
    ): MuligAnke? {
        val klagebehandling = klagebehandlingRepository.findByIdAndAvsluttetIsNotNull(klagebehandlingId) ?: return null
        return if (
            klagebehandling.klager.partId.value == partId && muligAnkeUtfall.contains(klagebehandling.currentDelbehandling().utfall)
        ) {
            klagebehandling.toMuligAnke()
        } else {
            null
        }
    }

    fun assignKlagebehandling(
        klagebehandlingId: UUID,
        tildeltSaksbehandlerIdent: String?,
        enhetId: String?,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, true)
        if (tildeltSaksbehandlerIdent != null) {
            //Denne sjekken gjøres kun når det er en tildeling:
            checkEnhetOgTemaTilgang(tildeltSaksbehandlerIdent, enhetId!!, klagebehandling)
        }
        val event =
            klagebehandling.setTildeling(
                tildeltSaksbehandlerIdent,
                enhetId,
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun switchMedunderskriverFlyt(
        klagebehandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandling(klagebehandlingId)

        if (klagebehandling.currentDelbehandling().medunderskriver?.saksbehandlerident == null) {
            throw KlagebehandlingManglerMedunderskriverException("Klagebehandlingen har ikke registrert noen medunderskriver")
        }

        if (klagebehandling.currentDelbehandling().medunderskriver?.saksbehandlerident == utfoerendeSaksbehandlerIdent) {
            checkMedunderskriverStatus(klagebehandling)
            if (klagebehandling.currentDelbehandling().medunderskriverFlyt != MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER) {
                val event = klagebehandling.setMedunderskriverFlyt(
                    MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER,
                    utfoerendeSaksbehandlerIdent
                )
                applicationEventPublisher.publishEvent(event)
            }
        } else {
            checkSkrivetilgang(klagebehandling)
            if (klagebehandling.currentDelbehandling().medunderskriverFlyt != MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER) {
                val event = klagebehandling.setMedunderskriverFlyt(
                    MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER,
                    utfoerendeSaksbehandlerIdent
                )
                applicationEventPublisher.publishEvent(event)
            }
        }

        return klagebehandling
    }

    fun setMedunderskriverIdentAndMedunderskriverFlyt(
        klagebehandlingId: UUID,
        medunderskriverIdent: String?,
        utfoerendeSaksbehandlerIdent: String,
        medunderskriverFlyt: MedunderskriverFlyt = MedunderskriverFlyt.IKKE_SENDT
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId)
        val event =
            klagebehandling.setMedunderskriverIdentAndMedunderskriverFlyt(
                medunderskriverIdent,
                medunderskriverFlyt,
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun createKlagebehandlingFromMottak(mottak: Mottak) {
        if (klagebehandlingRepository.findByMottakId(mottak.id) != null) {
            logger.error("We already have a klagebehandling for mottak ${mottak.id}. This is not supposed to happen.")
            throw RuntimeException("We already have a klagebehandling for mottak ${mottak.id}")
        }

        val klagebehandling = klagebehandlingRepository.save(
            Klagebehandling(
                klager = mottak.klager.copy(),
                sakenGjelder = mottak.sakenGjelder?.copy() ?: mottak.klager.toSakenGjelder(),
                ytelse = mottak.ytelse,
                type = mottak.type,
                kildeReferanse = mottak.kildeReferanse,
                dvhReferanse = mottak.dvhReferanse,
                sakFagsystem = mottak.sakFagsystem,
                sakFagsakId = mottak.sakFagsakId,
                innsendt = mottak.innsendtDato,
                mottattFoersteinstans = mottak.brukersHenvendelseMottattNavDato,
                avsenderEnhetFoersteinstans = mottak.forrigeBehandlendeEnhet,
                avsenderSaksbehandleridentFoersteinstans = mottak.forrigeSaksbehandlerident,
                mottattKlageinstans = mottak.sakMottattKaDato,
                tildeling = null,
                avsluttet = null,
                frist = mottak.generateFrist(),
                mottakId = mottak.id,
                delbehandlinger = setOf(Delbehandling()),
                saksdokumenter = createSaksdokumenter(mottak),
                kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(),
                hjemler = createHjemmelSetFromMottak(mottak.hjemler),
                kildesystem = mottak.kildesystem,
                kommentarFraFoersteinstans = mottak.kommentar
            )
        )
        logger.debug("Created behandling ${klagebehandling.id} for mottak ${mottak.id}")
        applicationEventPublisher.publishEvent(
            BehandlingEndretEvent(
                behandling = klagebehandling,
                endringslogginnslag = emptyList()
            )
        )
    }

    private fun createHjemmelSetFromMottak(hjemler: Set<MottakHjemmel>?): MutableSet<Hjemmel> =
        if (hjemler == null || hjemler.isEmpty()) {
            mutableSetOf(Hjemmel.MANGLER)
        } else {
            hjemler.map { Hjemmel.of(it.hjemmelId) }.toMutableSet()
        }


    private fun Klager.toSakenGjelder() = SakenGjelder(
        partId = this.partId.copy(),
        skalMottaKopi = false // Siden denne nå peker på samme som klager trenger ikke brev sendes
    )

    private fun createSaksdokumenter(mottak: Mottak): MutableSet<Saksdokument> {
        val saksdokumenter: MutableSet<Saksdokument> = mutableSetOf()
        mottak.mottakDokument.forEach {
            //TODO: Mangler å få med MottakDokument.type over i Saksdokument!
            saksdokumenter.addAll(createSaksdokument(it.journalpostId))
        }
        return saksdokumenter
    }

    private fun createSaksdokument(journalpostId: String) =
        dokumentService.fetchDokumentInfoIdForJournalpostAsSystembruker(journalpostId)
            .map { Saksdokument(journalpostId = journalpostId, dokumentInfoId = it) }

    private fun addDokument(
        klagebehandling: Klagebehandling,
        journalpostId: String,
        dokumentInfoId: String,
        saksbehandlerIdent: String
    ) {
        try {
            val foundSaksdokument =
                klagebehandling.saksdokumenter.find { it.journalpostId == journalpostId && it.dokumentInfoId == dokumentInfoId }
            if (foundSaksdokument != null) {
                logger.debug("Dokument (journalpost: $journalpostId dokumentInfoId: $dokumentInfoId) is already connected to klagebehandling ${klagebehandling.id}, doing nothing")
            } else {
                val saksdokument = Saksdokument(
                    journalpostId = journalpostId,
                    dokumentInfoId = dokumentInfoId
                )
                val event = klagebehandling.addSaksdokument(
                    saksdokument,
                    saksbehandlerIdent
                )
                event?.let { applicationEventPublisher.publishEvent(it) }
            }
        } catch (e: Exception) {
            logger.error("Error connecting journalpost $journalpostId to klagebehandling ${klagebehandling.id}", e)
            throw e
        }
    }

    private fun removeDokument(
        klagebehandling: Klagebehandling,
        saksdokument: Saksdokument,
        saksbehandlerIdent: String
    ): Klagebehandling {
        try {
            val event =
                klagebehandling.removeSaksdokument(
                    saksdokument,
                    saksbehandlerIdent
                )
            event.let { applicationEventPublisher.publishEvent(it) }

            return klagebehandling
        } catch (e: Exception) {
            logger.error("Error disconnecting document ${saksdokument.id} to klagebehandling ${klagebehandling.id}", e)
            throw e
        }
    }

    fun fetchDokumentlisteForKlagebehandling(
        klagebehandlingId: UUID,
        temaer: List<Tema>,
        pageSize: Int,
        previousPageRef: String?
    ): DokumenterResponse {
        val klagebehandling = getKlagebehandling(klagebehandlingId)
        return dokumentService.fetchDokumentlisteForKlagebehandling(klagebehandling, temaer, pageSize, previousPageRef)
    }

    fun fetchJournalposterConnectedToKlagebehandling(klagebehandlingId: UUID): DokumenterResponse {
        val klagebehandling = getKlagebehandling(klagebehandlingId)
        return dokumentService.fetchJournalposterConnectedToKlagebehandling(klagebehandling)
    }

    fun connectDokumentToKlagebehandling(
        klagebehandlingId: UUID,
        journalpostId: String,
        dokumentInfoId: String,
        saksbehandlerIdent: String
    ): LocalDateTime {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId)
        dokumentService.validateJournalpostExists(journalpostId)
        addDokument(
            klagebehandling,
            journalpostId,
            dokumentInfoId,
            saksbehandlerIdent
        )
        return klagebehandling.modified
    }

    fun disconnectDokumentFromKlagebehandling(
        klagebehandlingId: UUID,
        journalpostId: String,
        dokumentInfoId: String,
        saksbehandlerIdent: String
    ): LocalDateTime {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId)
        val saksdokument =
            klagebehandling.saksdokumenter.find { it.journalpostId == journalpostId && it.dokumentInfoId == dokumentInfoId }

        if (saksdokument == null) {
            logger.warn("no saksdokument found based on id $journalpostId/$dokumentInfoId")
        } else {
            removeDokument(
                klagebehandling,
                saksdokument,
                saksbehandlerIdent
            )
        }
        return klagebehandling.modified
    }

    @Transactional(readOnly = true)
    fun findKlagebehandlingForDistribusjon(): List<UUID> =
        klagebehandlingRepository.findByAvsluttetIsNullAndAvsluttetAvSaksbehandlerIsNotNull().map { it.id }

    private fun markerKlagebehandlingSomAvsluttetAvSaksbehandler(
        klagebehandling: Klagebehandling,
        innloggetIdent: String
    ): Klagebehandling {
        val event =
            klagebehandling.setAvsluttetAvSaksbehandler(innloggetIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun ferdigstillKlagebehandling(
        klagebehandlingId: UUID,
        innloggetIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(
            klagebehandlingId = klagebehandlingId
        )

        if (klagebehandling.avsluttetAvSaksbehandler != null) throw KlagebehandlingFinalizedException("Klagebehandlingen er avsluttet")

        //Forretningsmessige krav før vedtak kan ferdigstilles
        validateKlagebehandlingBeforeFinalize(klagebehandling)

        //Her settes en markør som så brukes async i kallet klagebehandlingRepository.findByAvsluttetIsNullAndAvsluttetAvSaksbehandlerIsNotNull
        return markerKlagebehandlingSomAvsluttetAvSaksbehandler(klagebehandling, innloggetIdent)
    }

    fun validateKlagebehandlingBeforeFinalize(klagebehandling: Klagebehandling) {
        val validationErrors = mutableListOf<InvalidProperty>()
        val sectionList = mutableListOf<ValidationSection>()

        if (harIkkeLagretVedtaksdokument(klagebehandling)) {
            validationErrors.add(
                InvalidProperty(
                    field = "vedtaksdokument",
                    reason = "Mangler vedtaksdokument"
                )
            )
        }
        if (klagebehandling.currentDelbehandling().utfall == null) {
            validationErrors.add(
                InvalidProperty(
                    field = "utfall",
                    reason = "Utfall er ikke satt på vedtak"
                )
            )
        }
        if (klagebehandling.currentDelbehandling().utfall != Utfall.TRUKKET) {
            if (klagebehandling.currentDelbehandling().hjemler.isEmpty()) {
                validationErrors.add(
                    InvalidProperty(
                        field = "hjemmel",
                        reason = "Hjemmel er ikke satt på vedtak"
                    )
                )
            }
        }

        if (validationErrors.isNotEmpty()) {
            sectionList.add(
                ValidationSection(
                    section = "klagebehandling",
                    properties = validationErrors
                )
            )
        }

        val kvalitetsvurderingValidationErrors = kakaApiGateway.getValidationErrors(klagebehandling)

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

    private fun harIkkeLagretVedtaksdokument(klagebehandling: Klagebehandling) =
        !(harLastetOppHovedDokumentTilDokumentEnhet(klagebehandling))

    private fun harLastetOppHovedDokumentTilDokumentEnhet(klagebehandling: Klagebehandling) =
        klagebehandling.currentDelbehandling().dokumentEnhetId != null && kabalDocumentGateway.isHovedDokumentUploaded(klagebehandling.currentDelbehandling().dokumentEnhetId!!)

    private fun Klagebehandling.toMuligAnke(): MuligAnke = MuligAnke(
        this.id,
        this.ytelse.toTema(),
        this.currentDelbehandling().utfall!!,
        this.innsendt!!,
        this.avsluttetAvSaksbehandler!!,
        this.klager.partId.value
    )
}
