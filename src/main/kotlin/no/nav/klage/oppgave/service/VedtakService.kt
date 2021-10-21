package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.VedtakVedleggInput
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setDokumentEnhetIdOgOpplastetInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setHjemlerInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setMellomlagerIdOgOpplastetInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setOpplastetInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setUtfallInVedtak
import no.nav.klage.oppgave.domain.klage.Vedtak
import no.nav.klage.oppgave.domain.kodeverk.Hjemmel
import no.nav.klage.oppgave.domain.kodeverk.Utfall
import no.nav.klage.oppgave.exceptions.VedtakFinalizedException
import no.nav.klage.oppgave.util.AttachmentValidator
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class VedtakService(
    private val klagebehandlingService: KlagebehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val attachmentValidator: AttachmentValidator,
    private val tilgangService: TilgangService,
    private val fileApiService: FileApiService,
    private val kabalDocumentGateway: KabalDocumentGateway
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Transactional(readOnly = true)
    fun getVedtak(klagebehandling: Klagebehandling): Vedtak {
        return klagebehandling.getVedtakOrException()
    }

    fun setUtfall(
        klagebehandlingId: UUID,
        utfall: Utfall?,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdate(
            klagebehandlingId
        )
        val event =
            klagebehandling.setUtfallInVedtak(utfall, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun setHjemler(
        klagebehandlingId: UUID,
        hjemler: Set<Hjemmel>,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdate(
            klagebehandlingId
        )
        val event =
            klagebehandling.setHjemlerInVedtak(hjemler, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun slettFilTilknyttetVedtak(
        klagebehandlingId: UUID,
        innloggetIdent: String
    ): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdate(
            klagebehandlingId
        )

        //TODO: Burde man sjekket tilgang til EnhetOgTema, ikke bare enhet?
        tilgangService.verifyInnloggetSaksbehandlersTilgangTilEnhet(klagebehandling.tildeling!!.enhet!!)

        val vedtak = klagebehandling.getVedtakOrException()

        var oppdatertKlagebehandling = if (vedtak.mellomlagerId != null) {
            slettMellomlagretDokument(
                klagebehandling,
                vedtak,
                innloggetIdent
            )
        } else klagebehandling

        oppdatertKlagebehandling = if (vedtak.dokumentEnhetId != null) {
            deleteHovedDokument(oppdatertKlagebehandling, innloggetIdent, vedtak.dokumentEnhetId!!)
        } else oppdatertKlagebehandling

        return oppdatertKlagebehandling
    }

    fun knyttVedtaksFilTilVedtak(
        klagebehandlingId: UUID,
        input: VedtakVedleggInput,
        innloggetIdent: String
    ): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdate(
            klagebehandlingId
        )

        tilgangService.verifyInnloggetSaksbehandlersTilgangTilEnhet(klagebehandling.tildeling!!.enhet!!)
        attachmentValidator.validateAttachment(input.vedlegg)
        if (klagebehandling.avsluttetAvSaksbehandler != null) throw VedtakFinalizedException("Klagebehandlingen er avsluttet")

        var oppdatertKlagebehandling = if (klagebehandling.getVedtakOrException().dokumentEnhetId == null) {
            createDokumentEnhet(klagebehandling, innloggetIdent)
        } else klagebehandling

        oppdatertKlagebehandling =
            uploadHovedDokument(
                oppdatertKlagebehandling,
                innloggetIdent,
                oppdatertKlagebehandling.getVedtakOrException().dokumentEnhetId!!,
                input.vedlegg
            )

        //Rydd opp gammel moro:
        if (klagebehandling.getVedtakOrException().mellomlagerId != null) {
            fileApiService.deleteDocument(oppdatertKlagebehandling.getVedtakOrException().mellomlagerId!!)
        }

        return oppdatertKlagebehandling
    }

    private fun slettMellomlagretDokument(
        klagebehandling: Klagebehandling,
        vedtak: Vedtak,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        if (vedtak.mellomlagerId != null) {
            fileApiService.deleteDocument(vedtak.mellomlagerId!!)
        }
        return setMellomlagerIdOgOpplastet(
            klagebehandling,
            null,
            utfoerendeSaksbehandlerIdent
        )
    }

    private fun setMellomlagerIdOgOpplastet(
        klagebehandling: Klagebehandling,
        mellomlagerId: String?,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val event =
            klagebehandling.setMellomlagerIdOgOpplastetInVedtak(mellomlagerId, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    private fun setDokumentEnhetIdOgOpplastet(
        klagebehandling: Klagebehandling,
        dokumentEnhetId: UUID?,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val event =
            klagebehandling.setDokumentEnhetIdOgOpplastetInVedtak(dokumentEnhetId, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    private fun setOpplastet(
        klagebehandling: Klagebehandling,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val event =
            klagebehandling.setOpplastetInVedtak(LocalDateTime.now(), utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    private fun createDokumentEnhet(
        klagebehandling: Klagebehandling,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val dokumentEnhetId: UUID = kabalDocumentGateway.createDokumentEnhet(klagebehandling)
        val oppdatertKlagebehandling =
            setDokumentEnhetIdOgOpplastet(klagebehandling, dokumentEnhetId, utfoerendeSaksbehandlerIdent)
        return oppdatertKlagebehandling
    }

    private fun deleteHovedDokument(
        klagebehandling: Klagebehandling,
        utfoerendeSaksbehandlerIdent: String,
        dokumentEnhetId: UUID
    ): Klagebehandling {
        kabalDocumentGateway.deleteHovedDokument(dokumentEnhetId)
        return setOpplastet(klagebehandling, utfoerendeSaksbehandlerIdent)
    }

    private fun uploadHovedDokument(
        klagebehandling: Klagebehandling,
        utfoerendeSaksbehandlerIdent: String,
        dokumentEnhetId: UUID,
        file: MultipartFile
    ): Klagebehandling {
        kabalDocumentGateway.uploadHovedDokument(dokumentEnhetId, file)
        return setOpplastet(klagebehandling, utfoerendeSaksbehandlerIdent)
    }
}
