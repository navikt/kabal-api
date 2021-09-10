package no.nav.klage.oppgave.service.distribusjon

import no.nav.klage.oppgave.clients.dokdistfordeling.DokDistFordelingClient
import no.nav.klage.oppgave.clients.saf.graphql.SafGraphQlClient
import no.nav.klage.oppgave.domain.joark.DokumentStatus
import no.nav.klage.oppgave.domain.klage.BrevMottaker
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setDokdistReferanseInVedtaksmottaker
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setJournalpostIdInBrevmottaker
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setMellomlagerIdInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setMellomlagerIdOgOpplastetInVedtak
import no.nav.klage.oppgave.domain.klage.KlagebehandlingAggregatFunctions.setVedtakFerdigDistribuert
import no.nav.klage.oppgave.domain.klage.Vedtak
import no.nav.klage.oppgave.exceptions.JournalpostNotFoundException
import no.nav.klage.oppgave.gateway.JournalpostGateway
import no.nav.klage.oppgave.service.FileApiService
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class VedtakDistribusjonService(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val dokDistFordelingClient: DokDistFordelingClient,
    private val fileApiService: FileApiService,
    private val klagebehandlingService: KlagebehandlingService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        const val SYSTEMBRUKER = "SYSTEMBRUKER" //TODO ??
        const val SYSTEM_JOURNALFOERENDE_ENHET = "9999"
    }

    fun distribuerJournalpostTilMottaker(
        klagebehandlingId: UUID,
        vedtakId: UUID,
        mottakerId: UUID
    ): Klagebehandling {
        val klagebehandling =
            klagebehandlingService.getKlagebehandlingForUpdateBySystembruker(klagebehandlingId, null)
        val vedtak = klagebehandling.getVedtak(vedtakId)
        val mottaker = vedtak.getMottaker(mottakerId)
        try {
            val dokdistReferanse: UUID =
                dokDistFordelingClient.distribuerJournalpost(mottaker.journalpostId!!).bestillingsId
            return setDokdistReferanse(klagebehandling, vedtak.id, mottaker.id, dokdistReferanse, SYSTEMBRUKER)
        } catch (e: Exception) {
            logger.warn("Kunne ikke distribuere journalpost ${mottaker.journalpostId}")
            throw e
        }
    }

    private fun setDokdistReferanse(
        klagebehandling: Klagebehandling,
        vedtakId: UUID,
        mottakerId: UUID,
        dokdistReferanse: UUID,
        utfoerendeSaksbehandlerIdent: String
    ): Klagebehandling {
        val event = klagebehandling.setDokdistReferanseInVedtaksmottaker(
            vedtakId,
            mottakerId,
            dokdistReferanse,
            utfoerendeSaksbehandlerIdent
        )
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    @Transactional
    fun lagBrevmottakere(klagebehandlingId: UUID, vedtakId: UUID): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdateBySystembruker(klagebehandlingId, null)
        logger.debug("Lager brevmottakere for vedtak $vedtakId i klagebehandling ${klagebehandling.id}")
        klagebehandling.lagBrevmottakereForVedtak(vedtakId)
        return klagebehandling
    }

    fun markerVedtakSomFerdigDistribuert(klagebehandlingId: UUID, vedtakId: UUID): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdateBySystembruker(klagebehandlingId, null)
        val event = klagebehandling.setVedtakFerdigDistribuert(vedtakId, SYSTEMBRUKER)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }

    fun slettMellomlagretDokument(
        klagebehandlingId: UUID,
        vedtakId: UUID
    ): Klagebehandling {
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdateBySystembruker(klagebehandlingId, null)
        val vedtak = klagebehandling.getVedtak(vedtakId)
        fileApiService.deleteDocument(vedtak.mellomlagerId!!)
        val event = klagebehandling.setMellomlagerIdInVedtak(vedtakId, null, SYSTEMBRUKER)
        applicationEventPublisher.publishEvent(event)
        return klagebehandling
    }
}
