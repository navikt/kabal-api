package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.VedtakFullfoerInput
import no.nav.klage.oppgave.api.view.VedtakSlettVedleggInput
import no.nav.klage.oppgave.api.view.VedtakVedleggInput
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setGrunnInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setHjemlerInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setJournalpostIdOgOpplastetInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setMellomlagerIdOgOpplastetInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setUtfallInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setVedtakAvsluttetAvSaksbehandler
import no.nav.klage.oppgave.domain.klage.Vedtak
import no.nav.klage.oppgave.domain.kodeverk.Grunn
import no.nav.klage.oppgave.domain.kodeverk.Hjemmel
import no.nav.klage.oppgave.domain.kodeverk.Utfall
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.exceptions.UtfallNotSetException
import no.nav.klage.oppgave.exceptions.VedtakFinalizedException
import no.nav.klage.oppgave.exceptions.VedtakNotFoundException
import no.nav.klage.oppgave.gateway.JournalpostGateway
import no.nav.klage.oppgave.util.AttachmentValidator
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class VedtakService(
    private val klagebehandlingService: KlagebehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val attachmentValidator: AttachmentValidator,
    private val tilgangService: TilgangService,
    private val fileApiService: FileApiService,
    private val journalpostGateway: JournalpostGateway
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Transactional(readOnly = true)
    fun getVedtak(klagebehandling: Klagebehandling, vedtakId: UUID): Vedtak {
        return klagebehandling.getVedtak(vedtakId)
    }

    fun setUtfall(
        klagebehandling: Klagebehandling,
        vedtakId: UUID,
        utfall: Utfall?,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val event =
            klagebehandling.setUtfallInVedtak(vedtakId, utfall, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setGrunn(
        klagebehandling: Klagebehandling,
        vedtakId: UUID,
        grunn: Grunn?,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val event =
            klagebehandling.setGrunnInVedtak(vedtakId, grunn, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setHjemler(
        klagebehandling: Klagebehandling,
        vedtakId: UUID,
        hjemler: Set<Hjemmel>,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val event =
            klagebehandling.setHjemlerInVedtak(vedtakId, hjemler, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setJournalpostIdOgOpplastet(
        klagebehandling: Klagebehandling,
        vedtakId: UUID,
        journalpostId: String?,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val event =
            klagebehandling.setJournalpostIdOgOpplastetInVedtak(vedtakId, journalpostId, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setMellomlagerIdOgOpplastet(
        klagebehandling: Klagebehandling,
        vedtakId: UUID,
        mellomlagerId: String?,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val event =
            klagebehandling.setMellomlagerIdOgOpplastetInVedtak(vedtakId, mellomlagerId, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun markerVedtakSomAvsluttetAvSaksbehandler(
        klagebehandling: Klagebehandling,
        vedtakId: UUID,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val event =
            klagebehandling.setVedtakAvsluttetAvSaksbehandler(vedtakId, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun slettFilTilknyttetVedtak(
        klagebehandlingId: UUID,
        vedtakId: UUID,
        input: VedtakSlettVedleggInput,
        innloggetIdent: String
    ): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdate(
            klagebehandlingId,
            input.klagebehandlingVersjon
        )

        //TODO: Burde man sjekket tilgang til EnhetOgTema, ikke bare enhet?
        tilgangService.verifyInnloggetSaksbehandlersTilgangTilEnhet(klagebehandling.tildeling!!.enhet!!)

        val vedtak = klagebehandling.getVedtak(vedtakId)

        if (vedtak.mellomlagerId == null && vedtak.journalpostId == null) {
            return klagebehandling
        }

        if (vedtak.journalpostId != null) {
            kansellerJournalpost(
                klagebehandling,
                vedtak,
                innloggetIdent
            )
        }

        if (vedtak.mellomlagerId != null) {
            slettMellomlagretDokument(
                klagebehandling,
                vedtak,
                innloggetIdent
            )
        }

        return klagebehandling
    }

    fun knyttVedtaksFilTilVedtak(
        klagebehandlingId: UUID,
        vedtakId: UUID,
        input: VedtakVedleggInput,
        innloggetIdent: String
    ): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdate(
            klagebehandlingId,
            input.klagebehandlingVersjon
        )

        //TODO: Burde man sjekket tilgang til EnhetOgTema, ikke bare enhet?
        tilgangService.verifyInnloggetSaksbehandlersTilgangTilEnhet(klagebehandling.tildeling!!.enhet!!)

        val vedtak = klagebehandling.getVedtak(vedtakId)

        if (vedtak.ferdigstiltIJoark != null) throw VedtakFinalizedException("Vedtak med id $vedtakId er ferdigstilt")

        if (vedtak.journalpostId != null) {
            kansellerJournalpost(
                klagebehandling,
                vedtak,
                innloggetIdent
            )
        }

        if (vedtak.mellomlagerId != null) {
            slettMellomlagretDokument(
                klagebehandling,
                vedtak,
                innloggetIdent
            )
        }

        attachmentValidator.validateAttachment(input.vedlegg)

        val mellomlagerId = fileApiService.uploadDocument(input.vedlegg)

        return setMellomlagerIdOgOpplastet(
            klagebehandling,
            vedtak.id,
            mellomlagerId,
            innloggetIdent
        )
    }

    fun ferdigstillVedtak(
        klagebehandlingId: UUID,
        vedtakId: UUID,
        input: VedtakFullfoerInput,
        innloggetIdent: String
    ): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdate(
            klagebehandlingId = klagebehandlingId,
            klagebehandlingVersjon = input.klagebehandlingVersjon,
            ignoreCheckSkrivetilgang = true
        )

        if (klagebehandling.medunderskriver?.saksbehandlerident != innloggetIdent) {
            secureLogger.error(
                "{} prøvde å fullføre vedtak for klagebehandling {}, men er ikke medunderskriver.",
                innloggetIdent,
                klagebehandlingId
            )
            throw MissingTilgangException("Vedtak kan kun ferdigstilles av medunderskriver")
        }

        val vedtak = klagebehandling.getVedtak(vedtakId)
        if (vedtak.ferdigstiltIJoark != null) throw VedtakFinalizedException("Vedtak med id $vedtakId er allerede ferdigstilt")

        if (vedtak.mellomlagerId == null && vedtak.journalpostId != null) {
            //Dette gjelder edge-case der noen har påbegynt en klagebehandling før vi innførte mellomlager.
            throw VedtakNotFoundException("Vennligst last opp vedtak på nytt")
        }

        if (vedtak.utfall == null) throw UtfallNotSetException("Utfall på vedtak $vedtakId er ikke satt")

        markerVedtakSomAvsluttetAvSaksbehandler(klagebehandling, vedtakId, innloggetIdent)

        if (klagebehandling.vedtak.all { it.avsluttetAvSaksbehandler != null }) {
            klagebehandlingService.markerKlagebehandlingSomAvsluttetAvSaksbehandler(klagebehandling, innloggetIdent)
        }
        return klagebehandling
    }

    private fun kansellerJournalpost(
        klagebehandling: Klagebehandling,
        vedtak: Vedtak,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        if (vedtak.journalpostId != null) {
            journalpostGateway.cancelJournalpost(vedtak.journalpostId!!)
        }
        return setJournalpostIdOgOpplastet(
            klagebehandling,
            vedtak.id,
            null,
            utfoerendeSaksbehandlerIdent
        )
    }

    fun slettMellomlagretDokument(
        klagebehandling: Klagebehandling,
        vedtak: Vedtak,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        if (vedtak.mellomlagerId != null) {
            fileApiService.deleteDocument(vedtak.mellomlagerId!!)
        }
        return setMellomlagerIdOgOpplastet(
            klagebehandling,
            vedtak.id,
            null,
            utfoerendeSaksbehandlerIdent
        )
    }
}
