package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.events.BehandlingEndretEvent
import no.nav.klage.oppgave.domain.klage.*
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setAvsluttetAvSaksbehandler
import no.nav.klage.oppgave.exceptions.*
import no.nav.klage.oppgave.repositories.KlagebehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class KlagebehandlingService(
    private val klagebehandlingRepository: KlagebehandlingRepository,
    private val tilgangService: TilgangService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val dokumentService: DokumentService,
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

    private fun checkSkrivetilgang(klagebehandling: Klagebehandling) {
        tilgangService.verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling)
    }

    private fun checkSkrivetilgangForSystembruker(klagebehandling: Klagebehandling) {
        tilgangService.verifySystembrukersSkrivetilgang(klagebehandling)
    }

    @Transactional(readOnly = true)
    fun getKlagebehandling(klagebehandlingId: UUID): Klagebehandling =
        klagebehandlingRepository.findById(klagebehandlingId)
            .orElseThrow { BehandlingNotFoundException("Klagebehandling med id $klagebehandlingId ikke funnet") }
            .also { checkLeseTilgang(it) }

    @Transactional(readOnly = true)
    fun getKlagebehandlingForReadWithoutCheckForAccess(klagebehandlingId: UUID): Klagebehandling =
        klagebehandlingRepository.findById(klagebehandlingId)
            .orElseThrow { BehandlingNotFoundException("Klagebehandling med id $klagebehandlingId ikke funnet") }

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
        klagebehandlingRepository.findByDelbehandlingerAvsluttetIsNotNull()
            .filter {
                it.klager.partId.value == partId &&
                        muligAnkeUtfall.contains(it.currentDelbehandling().utfall)
            }
            .map { it.toMuligAnke() }

    fun findMuligAnkeByPartIdAndKlagebehandlingId(
        partId: String,
        klagebehandlingId: UUID
    ): MuligAnke? {
        val klagebehandling = klagebehandlingRepository.findByIdAndDelbehandlingerAvsluttetIsNotNull(klagebehandlingId) ?: return null
        return if (
            klagebehandling.klager.partId.value == partId && muligAnkeUtfall.contains(klagebehandling.currentDelbehandling().utfall)
        ) {
            klagebehandling.toMuligAnke()
        } else {
            null
        }
    }

    fun createKlagebehandlingFromMottak(mottak: Mottak) {

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
                frist = mottak.generateFrist(),
                mottakId = mottak.id,
                delbehandlinger = setOf(Delbehandling()),
                saksdokumenter = dokumentService.createSaksdokumenterFromJournalpostIdSet(mottak.mottakDokument.map { it.journalpostId }),
                kakaKvalitetsvurderingId = kakaApiGateway.createKvalitetsvurdering(),
                hjemler = createHjemmelSetFromMottak(mottak.hjemler),
                kildesystem = mottak.kildesystem,
                kommentarFraFoersteinstans = mottak.kommentar
            )
        )
        logger.debug("Created klagebehandling ${klagebehandling.id} for mottak ${mottak.id}")
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

    @Transactional(readOnly = true)
    fun findKlagebehandlingForDistribusjon(): List<UUID> =
        klagebehandlingRepository.findByDelbehandlingerAvsluttetIsNullAndDelbehandlingerAvsluttetAvSaksbehandlerIsNotNull().map { it.id }

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

        if (klagebehandling.currentDelbehandling().avsluttetAvSaksbehandler != null) throw KlagebehandlingFinalizedException("Klagebehandlingen er avsluttet")

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
        this.currentDelbehandling().avsluttetAvSaksbehandler!!,
        this.klager.partId.value
    )
}
