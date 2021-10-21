package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.DokumenterResponse
import no.nav.klage.oppgave.api.view.KvalitetsvurderingManuellInput
import no.nav.klage.oppgave.domain.events.KlagebehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.addSaksdokument
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.removeSaksdokument
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setAvsenderEnhetFoersteinstans
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setAvsenderSaksbehandleridentFoersteinstans
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setAvsluttetAvSaksbehandler
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setFrist
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setInnsendt
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setMedunderskriverFlyt
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setMedunderskriverIdentAndMedunderskriverFlyt
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setMottattFoersteinstans
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setMottattKlageinstans
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setTema
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setTildeling
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setType
import no.nav.klage.oppgave.domain.kodeverk.*
import no.nav.klage.oppgave.exceptions.*
import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.util.*

@Service
@Transactional
class KlagebehandlingService(
    private val klagebehandlingRepository: KlagebehandlingRepository,
    private val tilgangService: TilgangService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val dokumentService: DokumentService,
    private val tokenUtil: TokenUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    private fun checkLeseTilgang(klagebehandling: Klagebehandling) {
        if (klagebehandling.sakenGjelder.erPerson()) {
            tilgangService.verifyInnloggetSaksbehandlersTilgangTil(klagebehandling.sakenGjelder.partId.value)
        }
        tilgangService.verifyInnloggetSaksbehandlersTilgangTilTema(klagebehandling.tema)
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
        tilgangService.verifySaksbehandlersTilgangTilEnhetOgTema(
            tildeltSaksbehandlerIdent,
            tildeltEnhetId,
            klagebehandling.tema
        )
    }

    @Transactional(readOnly = true)
    fun getKlagebehandling(klagebehandlingId: UUID): Klagebehandling =
        klagebehandlingRepository.findById(klagebehandlingId)
            .orElseThrow { KlagebehandlingNotFoundException("Klagebehandling med id $klagebehandlingId ikke funnet") }
            .also { checkLeseTilgang(it) }

    fun getKlagebehandlingForUpdate(
        klagebehandlingId: UUID,
        ignoreCheckSkrivetilgang: Boolean = false
    ): Klagebehandling =
        klagebehandlingRepository.getOne(klagebehandlingId)
            .also { checkLeseTilgang(it) }
            .also { if (!ignoreCheckSkrivetilgang) checkSkrivetilgang(it) }

    fun getKlagebehandlingForUpdateByMedunderskriver(
        klagebehandlingId: UUID,
    ): Klagebehandling =
        klagebehandlingRepository.getOne(klagebehandlingId)
            .also { checkLeseTilgang(it) }
            .also { checkMedunderskriverStatus(it) }

    fun getKlagebehandlingForUpdateBySystembruker(
        klagebehandlingId: UUID,
    ): Klagebehandling =
        klagebehandlingRepository.getOne(klagebehandlingId)
            .also { checkSkrivetilgangForSystembruker(it) }

    var muligAnkeUtfall = setOf(
        Utfall.MEDHOLD,
        Utfall.DELVIS_MEDHOLD,
        Utfall.OPPRETTHOLDT,
        Utfall.UGUNST,
        Utfall.AVVIST
    )

    fun findMuligAnkeByPartId(
        partId: String
    ): List<MuligAnke> =
        klagebehandlingRepository.findByAvsluttetIsNotNull()
            .filter {
                it.klager.partId.value == partId &&
                        muligAnkeUtfall.contains(it.vedtak?.utfall)
            }
            .map { it.toMuligAnke() }

    fun findMuligAnkeByPartIdAndKlagebehandlingId(
        partId: String,
        klagebehandlingId: UUID
    ): MuligAnke? {
        val klagebehandling = klagebehandlingRepository.findByIdAndAvsluttetIsNotNull(klagebehandlingId) ?: return null
        return if (
            klagebehandling.klager.partId.value == partId && muligAnkeUtfall.contains(klagebehandling.vedtak?.utfall)
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

        if (klagebehandling.medunderskriver?.saksbehandlerident == null) {
            throw KlagebehandlingManglerMedunderskriverException("Klagebehandlingen har ikke registrert noen medunderskriver")
        }

        if (klagebehandling.medunderskriver?.saksbehandlerident == utfoerendeSaksbehandlerIdent) {
            checkMedunderskriverStatus(klagebehandling)
            if (klagebehandling.medunderskriverFlyt != MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER) {
                val event = klagebehandling.setMedunderskriverFlyt(
                    MedunderskriverFlyt.RETURNERT_TIL_SAKSBEHANDLER,
                    utfoerendeSaksbehandlerIdent
                )
                applicationEventPublisher.publishEvent(event)
            }
        } else {
            checkSkrivetilgang(klagebehandling)
            if (klagebehandling.medunderskriverFlyt != MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER) {
                val event = klagebehandling.setMedunderskriverFlyt(
                    MedunderskriverFlyt.OVERSENDT_TIL_MEDUNDERSKRIVER,
                    utfoerendeSaksbehandlerIdent
                )
                applicationEventPublisher.publishEvent(event)
            }
        }

        return klagebehandling
    }

    fun setType(
        klagebehandlingId: UUID,
        type: Type,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId)
        val event =
            klagebehandling.setType(type, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setTema(
        klagebehandlingId: UUID,
        tema: Tema,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId)
        val event =
            klagebehandling.setTema(tema, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setInnsendt(
        klagebehandlingId: UUID,
        innsendt: LocalDate,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId)
        val event =
            klagebehandling.setInnsendt(innsendt, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setMottattFoersteinstans(
        klagebehandlingId: UUID,
        mottattFoersteinstans: LocalDate,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId)
        val event =
            klagebehandling.setMottattFoersteinstans(mottattFoersteinstans, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setMottattKlageinstans(
        klagebehandlingId: UUID,
        mottattKlageinstans: LocalDateTime,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId)
        val event =
            klagebehandling.setMottattKlageinstans(mottattKlageinstans, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setFrist(
        klagebehandlingId: UUID,
        frist: LocalDate,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId)
        val event =
            klagebehandling.setFrist(frist, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setAvsenderSaksbehandleridentFoersteinstans(
        klagebehandlingId: UUID,
        saksbehandlerIdent: String,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId)
        val event =
            klagebehandling.setAvsenderSaksbehandleridentFoersteinstans(
                saksbehandlerIdent,
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setAvsenderEnhetFoersteinstans(
        klagebehandlingId: UUID,
        enhet: String,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId)
        val event =
            klagebehandling.setAvsenderEnhetFoersteinstans(
                enhet,
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
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
                tema = mottak.tema,
                type = mottak.type,
                kildeReferanse = mottak.kildeReferanse,
                sakFagsystem = mottak.sakFagsystem,
                sakFagsakId = mottak.sakFagsakId,
                innsendt = mottak.innsendtDato,
                mottattFoersteinstans = mottak.mottattNavDato,
                avsenderEnhetFoersteinstans = mottak.avsenderEnhet,
                avsenderSaksbehandleridentFoersteinstans = mottak.avsenderSaksbehandlerident,
                mottattKlageinstans = mottak.oversendtKaDato,
                tildeling = null,
                avsluttet = null,
                frist = mottak.generateFrist(),
                mottakId = mottak.id,
                vedtak = Vedtak(),
                kvalitetsvurdering = null,
                hjemler = createHjemmelSetFromMottak(mottak.hjemmelListe),
                saksdokumenter = createSaksdokumenter(mottak),
                kildesystem = mottak.kildesystem,
                kommentarFraFoersteinstans = mottak.kommentar
            )
        )
        logger.debug("Created behandling ${klagebehandling.id} for mottak ${mottak.id}")
        applicationEventPublisher.publishEvent(
            KlagebehandlingEndretEvent(
                klagebehandling = klagebehandling,
                endringslogginnslag = emptyList()
            )
        )
    }

    fun createKlagebehandlingFromKvalitetsvurdering(
        kvalitetsvurdering: KvalitetsvurderingManuellInput,
        mottakId: UUID
    ): UUID {
        val klager = Klager(
            partId = PartId(
                type = PartIdType.PERSON,
                value = kvalitetsvurdering.foedselsnummer
            )
        )

        val hjemler = createHjemmelSetFromMottak(kvalitetsvurdering.hjemler)

        val klagebehandling = klagebehandlingRepository.save(
            Klagebehandling(
                klager = klager,
                sakenGjelder = klager.toSakenGjelder(),
                tema = kvalitetsvurdering.tema,
                type = Type.KLAGE, // TODO
                mottakId = mottakId,
                kildesystem = Fagsystem.MANUELL,
                mottattKlageinstans = kvalitetsvurdering.datoMottattKlageinstans,
                tildeling = Tildeling(
                    tokenUtil.getIdent(),
                    kvalitetsvurdering.tildeltKlageenhet,
                    kvalitetsvurdering.datoMottattKlageinstans
                ),
                hjemler = hjemler,
                //TODO New model
//                kvalitetsvurdering = Kvalitetsvurdering(
//                    eoes = kvalitetsvurdering.eoes,
//                    raadfoertMedLege = kvalitetsvurdering.raadfoertMedLege,
//                    internVurdering = kvalitetsvurdering.internVurdering,
//                    sendTilbakemelding = kvalitetsvurdering.sendTilbakemelding,
//                    tilbakemelding = kvalitetsvurdering.tilbakemelding,
//                    mottakerSaksbehandlerident = kvalitetsvurdering.foersteinstansSaksbehandler,
//                    mottakerEnhet = kvalitetsvurdering.foersteinstansEnhet
//                ),
                vedtak = Vedtak(
                    utfall = kvalitetsvurdering.utfall,
                    grunn = kvalitetsvurdering.grunn,
                    //TODO Dette må fikses når vi ordner kvalitetsvurdering uten klagebehandling på nytt. Bør vel kanskje puttes i saksdokumenter, ikke i vedtaket?
                    //journalpostId = kvalitetsvurdering.vedtaksbrevJournalpostId,
                    hjemler = hjemler
                )
            )
        )
        logger.debug("Created behandling ${klagebehandling.id} from manuell kvalitetsvurdering")
        applicationEventPublisher.publishEvent(
            KlagebehandlingEndretEvent(
                klagebehandling = klagebehandling,
                endringslogginnslag = emptyList()
            )
        )

        return klagebehandling.id
    }

    private fun createHjemmelSetFromMottak(hjemler: Set<MottakHjemmel>?): MutableSet<Hjemmel> =
        if (hjemler == null || hjemler.isEmpty()) {
            mutableSetOf(Hjemmel.MANGLER)
        } else {
            hjemler.mapNotNull { mapMottakHjemmel(it) }.toMutableSet()
        }


    private fun Klager.toSakenGjelder() = SakenGjelder(
        partId = this.partId.copy(),
        skalMottaKopi = false // Siden denne nå peker på samme som klager trenger ikke brev sendes
    )


    private fun mapMottakHjemmel(hjemmel: MottakHjemmel): Hjemmel? {
        return try {
            val lov = hjemmel.lov
            val kapittelOgParagraf = mapKapittelOgParagraf(hjemmel.kapittel, hjemmel.paragraf)
            Hjemmel.of(lov, kapittelOgParagraf)
        } catch (e: Exception) {
            logger.warn("Unable to map hjemmel", hjemmel, e)
            null
        }
    }

    private fun mapKapittelOgParagraf(kapittel: Int?, paragraf: Int?): KapittelOgParagraf? {
        return if (kapittel != null) {
            KapittelOgParagraf(kapittel, paragraf)
        } else null
    }

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

    private fun Mottak.generateFrist() = oversendtKaDato.toLocalDate() + Period.ofWeeks(12)

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

    private fun validateKlagebehandlingBeforeFinalize(klagebehandling: Klagebehandling) {
        if (klagebehandling.vedtak?.mellomlagerId == null && klagebehandling.vedtak?.dokumentEnhetId == null) {
            throw ResultatDokumentNotFoundException("Vennligst last opp vedtaksdokument på nytt")
        }
        if (klagebehandling.vedtak.utfall == null) {
            throw ValidationException("Utfall er ikke satt på vedtak")
        }
        if (klagebehandling.vedtak.utfall != Utfall.TRUKKET) {
            if (klagebehandling.vedtak.hjemler.isEmpty()) {
                throw ValidationException("Hjemmel er ikke satt på vedtak")
            }
            klagebehandling.kvalitetsvurdering?.validate()
        }
    }

    private fun Klagebehandling.toMuligAnke(): MuligAnke = MuligAnke(
        this.id,
        this.tema,
        this.vedtak?.utfall!!,
        this.innsendt!!,
        this.avsluttetAvSaksbehandler!!,
        this.klager.partId.value
    )
}
