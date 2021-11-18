package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.VedtakVedleggInput
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setDokumentEnhetIdInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setHjemlerInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setSmartEditorIdInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setUtfallInVedtak
import no.nav.klage.oppgave.domain.klage.Vedtak
import no.nav.klage.oppgave.domain.kodeverk.Hjemmel
import no.nav.klage.oppgave.domain.kodeverk.Utfall
import no.nav.klage.oppgave.exceptions.VedtakFinalizedException
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.util.*

@Service
@Transactional
class VedtakService(
    private val klagebehandlingService: KlagebehandlingService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val tilgangService: TilgangService,
    private val kabalDocumentGateway: KabalDocumentGateway
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Transactional(readOnly = true)
    fun getVedtak(klagebehandling: Klagebehandling): Vedtak {
        return klagebehandling.vedtak
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

        var oppdatertKlagebehandling = if (klagebehandling.vedtak.dokumentEnhetId != null) {
            deleteHovedDokument(klagebehandling, klagebehandling.vedtak.dokumentEnhetId!!)
        } else klagebehandling

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
        if (klagebehandling.avsluttetAvSaksbehandler != null) throw VedtakFinalizedException("Klagebehandlingen er avsluttet")

        var oppdatertKlagebehandling = if (klagebehandling.vedtak.dokumentEnhetId == null) {
            createDokumentEnhet(klagebehandling, innloggetIdent)
        } else klagebehandling

        oppdatertKlagebehandling =
            uploadHovedDokument(
                oppdatertKlagebehandling,
                oppdatertKlagebehandling.vedtak.dokumentEnhetId!!,
                input.vedlegg
            )

        return oppdatertKlagebehandling
    }

    private fun setDokumentEnhetIdOgOpplastet(
        klagebehandling: Klagebehandling,
        dokumentEnhetId: UUID?,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val event =
            klagebehandling.setDokumentEnhetIdInVedtak(dokumentEnhetId, utfoerendeSaksbehandlerIdent)
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
        dokumentEnhetId: UUID
    ): Klagebehandling {
        kabalDocumentGateway.deleteHovedDokument(dokumentEnhetId)
        return klagebehandling
    }

    private fun uploadHovedDokument(
        klagebehandling: Klagebehandling,
        dokumentEnhetId: UUID,
        file: MultipartFile
    ): Klagebehandling {
        kabalDocumentGateway.uploadHovedDokument(dokumentEnhetId, file)
        return klagebehandling
    }

    fun setSmartEditorId(
        klagebehandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String,
        smartEditorId: String?
    ): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdate(
            klagebehandlingId
        )
        val event =
            klagebehandling.setSmartEditorIdInVedtak(smartEditorId, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun deleteSmartEditorId(klagebehandlingId: UUID, utfoerendeSaksbehandlerIdent: String): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdate(
            klagebehandlingId
        )
        val event =
            klagebehandling.setSmartEditorIdInVedtak(nyVerdi = null, saksbehandlerident = utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }
}
