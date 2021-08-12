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
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setKvalitetsvurderingEoes
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setKvalitetsvurderingInternvurdering
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setKvalitetsvurderingRaadfoertMedLege
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setKvalitetsvurderingSendTilbakemelding
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setKvalitetsvurderingTilbakemelding
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setMedunderskriverident
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setMottattFoersteinstans
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setMottattKlageinstans
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setTema
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setTildeling
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setType
import no.nav.klage.oppgave.domain.kodeverk.*
import no.nav.klage.oppgave.exceptions.KlagebehandlingNotFoundException
import no.nav.klage.oppgave.exceptions.ValidationException
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
        klagebehandlingVersjon: Long? = null,
        ignoreCheckSkrivetilgang: Boolean = false
    ): Klagebehandling =
        klagebehandlingRepository.getOne(klagebehandlingId)
            .also { checkLeseTilgang(it) }
            .also { if (!ignoreCheckSkrivetilgang) checkSkrivetilgang(it) }
            .also { it.checkOptimisticLocking(klagebehandlingVersjon) }

    fun getKlagebehandlingForUpdateBySystembruker(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?
    ): Klagebehandling =
        klagebehandlingRepository.getOne(klagebehandlingId)
            .also { checkSkrivetilgangForSystembruker(it) }
            .also { it.checkOptimisticLocking(klagebehandlingVersjon) }

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
                muligAnkeUtfall.contains(it.vedtak.first().utfall)
            }
            .map { it.toMuligAnke() }

    fun findMuligAnkeByPartIdAndKlagebehandlingId(
        partId: String,
        klagebehandlingId: UUID
    ): MuligAnke? {
        val klagebehandling = klagebehandlingRepository.findByIdAndAvsluttetIsNotNull(klagebehandlingId) ?: return null
        return if (
            klagebehandling.klager.partId.value == partId && muligAnkeUtfall.contains(klagebehandling.vedtak.first().utfall)
        ) {
            klagebehandling.toMuligAnke()
        } else {
            null
        }
    }

    fun assignKlagebehandling(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?,
        tildeltSaksbehandlerIdent: String?,
        enhetId: String?,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, klagebehandlingVersjon, true)
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

    fun setType(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?,
        type: Type,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, klagebehandlingVersjon)
        val event =
            klagebehandling.setType(type, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setTema(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?,
        tema: Tema,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, klagebehandlingVersjon)
        val event =
            klagebehandling.setTema(tema, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setInnsendt(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?,
        innsendt: LocalDate,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, klagebehandlingVersjon)
        val event =
            klagebehandling.setInnsendt(innsendt, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setMottattFoersteinstans(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?,
        mottattFoersteinstans: LocalDate,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, klagebehandlingVersjon)
        val event =
            klagebehandling.setMottattFoersteinstans(mottattFoersteinstans, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setMottattKlageinstans(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?,
        mottattKlageinstans: LocalDateTime,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, klagebehandlingVersjon)
        val event =
            klagebehandling.setMottattKlageinstans(mottattKlageinstans, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setFrist(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?,
        frist: LocalDate,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, klagebehandlingVersjon)
        val event =
            klagebehandling.setFrist(frist, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setAvsenderSaksbehandleridentFoersteinstans(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?,
        saksbehandlerIdent: String,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, klagebehandlingVersjon)
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
        klagebehandlingVersjon: Long?,
        enhet: String,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, klagebehandlingVersjon)
        val event =
            klagebehandling.setAvsenderEnhetFoersteinstans(
                enhet,
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setMedunderskriverident(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?,
        medunderskriverIdent: String,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, klagebehandlingVersjon)
        validateBeforeMedunderskriver(klagebehandling)
        val event =
            klagebehandling.setMedunderskriverident(
                medunderskriverIdent,
                utfoerendeSaksbehandlerIdent
            )
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    private fun validateBeforeMedunderskriver(klagebehandling: Klagebehandling) {
        if (klagebehandling.vedtak.isEmpty()) {
            throw ValidationException("Klagebehandling har ikke vedtak")
        }
        if (klagebehandling.vedtak.first().utfall == null) {
            throw ValidationException("Utfall er ikke satt på vedtak")
        }
        if (klagebehandling.vedtak.first().utfall in listOf(Utfall.OPPHEVET, Utfall.MEDHOLD, Utfall.DELVIS_MEDHOLD)) {
            if (klagebehandling.vedtak.first().grunn == null) {
                throw ValidationException("Omgjøringsgrunn er ikke satt på vedtak")
            }
        }
        if (klagebehandling.vedtak.first().hjemler.isNullOrEmpty()) {
            throw ValidationException("Hjemmel er ikke satt på vedtak")
        }
    }

    fun setKvalitetsvurderingEoes(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?,
        eoes: Eoes?,
        saksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, klagebehandlingVersjon)
        val event = klagebehandling.setKvalitetsvurderingEoes(eoes, saksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setKvalitetsvurderingRaadfoertMedLege(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?,
        raadfoertMedLege: RaadfoertMedLege?,
        saksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = getKlagebehandlingForUpdate(klagebehandlingId, klagebehandlingVersjon)
        val event = klagebehandling.setKvalitetsvurderingRaadfoertMedLege(raadfoertMedLege, saksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setKvalitetsvurderingInternVurdering(
        klagebehandling: Klagebehandling,
        internVurdering: String?,
        saksbehandlerIdent: String
    ): Klagebehandling {
        val event = klagebehandling.setKvalitetsvurderingInternvurdering(internVurdering, saksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setKvalitetsvurderingSendTilbakemelding(
        klagebehandling: Klagebehandling,
        sendTilbakemelding: Boolean?,
        saksbehandlerIdent: String
    ): Klagebehandling {
        val event = klagebehandling.setKvalitetsvurderingSendTilbakemelding(sendTilbakemelding, saksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setKvalitetsvurderingTilbakemelding(
        klagebehandling: Klagebehandling,
        tilbakemelding: String?,
        saksbehandlerIdent: String
    ): Klagebehandling {
        val event = klagebehandling.setKvalitetsvurderingTilbakemelding(tilbakemelding, saksbehandlerIdent)
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
                vedtak = mutableSetOf(Vedtak()),
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
                kvalitetsvurdering = Kvalitetsvurdering(
                    eoes = kvalitetsvurdering.eoes,
                    raadfoertMedLege = kvalitetsvurdering.raadfoertMedLege,
                    internVurdering = kvalitetsvurdering.internVurdering,
                    sendTilbakemelding = kvalitetsvurdering.sendTilbakemelding,
                    tilbakemelding = kvalitetsvurdering.tilbakemelding,
                    mottakerSaksbehandlerident = kvalitetsvurdering.foersteinstansSaksbehandler,
                    mottakerEnhet = kvalitetsvurdering.foersteinstansEnhet
                ),
                vedtak = mutableSetOf(
                    Vedtak(
                        utfall = kvalitetsvurdering.utfall,
                        grunn = kvalitetsvurdering.grunn,
                        journalpostId = kvalitetsvurdering.vedtaksbrevJournalpostId,
                        hjemler = hjemler
                    )
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

    fun addDokument(
        klagebehandling: Klagebehandling,
        journalpostId: String,
        dokumentInfoId: String,
        saksbehandlerIdent: String
    ): Klagebehandling {
        try {
            if (klagebehandling.saksdokumenter.any { it.journalpostId == journalpostId && it.dokumentInfoId == dokumentInfoId }) {
                logger.debug("Dokument (journalpost: $journalpostId dokumentInfoId: $dokumentInfoId) is already connected to klagebehandling ${klagebehandling.id}, doing nothing")
            } else {
                val event =
                    klagebehandling.addSaksdokument(
                        Saksdokument(
                            journalpostId = journalpostId,
                            dokumentInfoId = dokumentInfoId
                        ), saksbehandlerIdent
                    )
                event?.let { applicationEventPublisher.publishEvent(it) }
            }
            return klagebehandling
        } catch (e: Exception) {
            logger.error("Error connecting journalpost $journalpostId to klagebehandling ${klagebehandling.id}", e)
            throw e
        }
    }

    fun removeDokument(
        klagebehandling: Klagebehandling,
        journalpostId: String,
        dokumentInfoId: String,
        saksbehandlerIdent: String
    ): Klagebehandling {
        try {
            if (klagebehandling.saksdokumenter.none { it.journalpostId == journalpostId && it.dokumentInfoId == dokumentInfoId }) {
                logger.debug("Dokument (journalpost: $journalpostId dokumentInfoId: $dokumentInfoId) is not connected to klagebehandling ${klagebehandling.id}, doing nothing")
            } else {
                val event =
                    klagebehandling.removeSaksdokument(
                        Saksdokument(
                            journalpostId = journalpostId,
                            dokumentInfoId = dokumentInfoId
                        ), saksbehandlerIdent
                    )
                event?.let { applicationEventPublisher.publishEvent(it) }
            }
            return klagebehandling
        } catch (e: Exception) {
            logger.error("Error disconnecting journalpost $journalpostId to klagebehandling ${klagebehandling.id}", e)
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
        klagebehandling: Klagebehandling,
        journalpostId: String,
        dokumentInfoId: String,
        saksbehandlerIdent: String
    ) {
        dokumentService.validateJournalpostExists(journalpostId)
        addDokument(
            klagebehandling,
            journalpostId,
            dokumentInfoId,
            saksbehandlerIdent
        )
    }

    fun disconnectDokumentFromKlagebehandling(
        klagebehandling: Klagebehandling,
        journalpostId: String,
        dokumentInfoId: String,
        saksbehandlerIdent: String
    ) {
        removeDokument(
            klagebehandling,
            journalpostId,
            dokumentInfoId,
            saksbehandlerIdent
        )
    }

    fun toggleDokumentFromKlagebehandling(
        klagebehandlingId: UUID,
        klagebehandlingVersjon: Long?,
        journalpostId: String,
        dokumentInfoId: String,
        saksbehandlerIdent: String
    ): Boolean {
        val klagebehandling = klagebehandlingRepository.getOne(klagebehandlingId)

        if (klagebehandling.saksdokumenter.none { it.journalpostId == journalpostId && it.dokumentInfoId == dokumentInfoId }) {
            connectDokumentToKlagebehandling(
                klagebehandling,
                journalpostId,
                dokumentInfoId,
                saksbehandlerIdent
            )
            return true
        } else {
            disconnectDokumentFromKlagebehandling(
                klagebehandling,
                journalpostId,
                dokumentInfoId,
                saksbehandlerIdent
            )
            return false
        }
    }

    private fun Mottak.generateFrist() = oversendtKaDato.toLocalDate() + Period.ofWeeks(12)

    @Transactional(readOnly = true)
    fun findKlagebehandlingForDistribusjon(): List<UUID> =
        klagebehandlingRepository.findByAvsluttetIsNullAndAvsluttetAvSaksbehandlerIsNotNull().map { it.id }

    fun markerKlagebehandlingSomAvsluttetAvSaksbehandler(
        klagebehandling: Klagebehandling,
        innloggetIdent: String
    ): Klagebehandling {
        val event =
            klagebehandling.setAvsluttetAvSaksbehandler(innloggetIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    private fun Klagebehandling.toMuligAnke(): MuligAnke = MuligAnke(
        this.id,
        this.tema,
        this.vedtak.first().utfall!!,
        this.innsendt!!,
        this.avsluttetAvSaksbehandler!!,
        this.klager.partId.value
    )

}
