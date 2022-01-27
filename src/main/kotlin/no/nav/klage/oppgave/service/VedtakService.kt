package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.oppgave.api.view.VedtakVedleggInput
import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.clients.kabaldocument.model.response.JournalpostId
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setDokumentEnhetIdInVedtak
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setHjemlerInVedtak
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setHovedadressatJournalpostIdInVedtak
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setSmartEditorIdInVedtak
import no.nav.klage.oppgave.domain.klage.BehandlingAggregatFunctions.setUtfallInVedtak
import no.nav.klage.oppgave.domain.klage.Delbehandling
import no.nav.klage.oppgave.domain.klage.Klagebehandling
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
    private val behandlingService: BehandlingService,
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
    fun getVedtak(behandling: Behandling): Delbehandling {
        return behandling.currentDelbehandling()
    }

    fun setUtfall(
        behandlingId: UUID,
        utfall: Utfall?,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
        val behandling = behandlingService.getBehandlingForUpdate(
            behandlingId
        )
        val event =
            behandling.setUtfallInVedtak(utfall, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return behandling
    }

    fun setHjemler(
        behandlingId: UUID,
        hjemler: Set<Registreringshjemmel>,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
        val behandling = behandlingService.getBehandlingForUpdate(
            behandlingId
        )
        val event =
            behandling.setHjemlerInVedtak(hjemler, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return behandling
    }

    fun slettFilTilknyttetVedtak(
        behandlingId: UUID,
        innloggetIdent: String
    ): Behandling {
        val behandling = behandlingService.getBehandlingForUpdate(
            behandlingId
        )

        //TODO: Burde man sjekket tilgang til EnhetOgTema, ikke bare enhet?
        tilgangService.verifyInnloggetSaksbehandlersTilgangTilEnhet(behandling.tildeling!!.enhet!!)

        var oppdatertBehandling = if (behandling.currentDelbehandling().dokumentEnhetId != null) {
            deleteHovedDokument(behandling, behandling.currentDelbehandling().dokumentEnhetId!!)
        } else behandling

        return oppdatertBehandling
    }

    fun knyttVedtaksFilTilVedtak(
        behandlingId: UUID,
        input: VedtakVedleggInput,
        innloggetIdent: String
    ): Behandling {
        val behandling = behandlingService.getBehandlingForUpdate(
            behandlingId
        )

        tilgangService.verifyInnloggetSaksbehandlersTilgangTilEnhet(behandling.tildeling!!.enhet!!)
        if (behandling.currentDelbehandling().avsluttetAvSaksbehandler != null) throw VedtakFinalizedException("Behandlingen er avsluttet")

        var oppdatertBehandling = if (behandling.currentDelbehandling().dokumentEnhetId == null) {
            createDokumentEnhet(behandling, innloggetIdent)
        } else behandling

        oppdatertBehandling =
            uploadHovedDokument(
                oppdatertBehandling,
                oppdatertBehandling.currentDelbehandling().dokumentEnhetId!!,
                input.vedlegg
            )

        return oppdatertBehandling
    }

    private fun setDokumentEnhetIdOgOpplastet(
        behandling: Behandling,
        dokumentEnhetId: UUID?,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
        val event =
            behandling.setDokumentEnhetIdInVedtak(dokumentEnhetId, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return behandling
    }

    private fun createDokumentEnhet(
        behandling: Behandling,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
        val dokumentEnhetId: UUID = kabalDocumentGateway.createDokumentEnhet(behandling)
        val oppdatertBehandling =
            setDokumentEnhetIdOgOpplastet(behandling, dokumentEnhetId, utfoerendeSaksbehandlerIdent)
        return oppdatertBehandling
    }

    private fun deleteHovedDokument(
        behandling: Behandling,
        dokumentEnhetId: UUID
    ): Behandling {
        kabalDocumentGateway.deleteHovedDokument(dokumentEnhetId)
        return behandling
    }

    private fun uploadHovedDokument(
        behandling: Behandling,
        dokumentEnhetId: UUID,
        file: MultipartFile
    ): Behandling {
        kabalDocumentGateway.uploadHovedDokument(dokumentEnhetId, file)
        return behandling
    }

    fun setSmartEditorId(
        behandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String,
        smartEditorId: String?
    ): Behandling {
        val behandling = behandlingService.getBehandlingForUpdate(
            behandlingId
        )
        val event =
            behandling.setSmartEditorIdInVedtak(smartEditorId, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return behandling
    }

    fun addHovedadressatJournalpostId(
        klagebehandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String,
        journalpostId: JournalpostId
    ): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdateBySystembruker(
            klagebehandlingId
        )

        val event =
            klagebehandling.setHovedadressatJournalpostIdInVedtak(journalpostId.value, utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling

    }

    fun deleteSmartEditorId(
        behandlingId: UUID,
        utfoerendeSaksbehandlerIdent: String
    ): Behandling {
        val behandling = behandlingService.getBehandlingForUpdate(
            behandlingId
        )
        val event =
            behandling.setSmartEditorIdInVedtak(nyVerdi = null, saksbehandlerident = utfoerendeSaksbehandlerIdent)
        applicationEventPublisher.publishEvent(event)
        return behandling
    }
}
