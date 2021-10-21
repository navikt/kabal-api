package no.nav.klage.oppgave.service.distribusjon

import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class KlagebehandlingDistribusjonService(
    private val klagebehandlingService: KlagebehandlingService,
    private val vedtakDistribusjonService: VedtakDistribusjonService,
    private val klagebehandlingAvslutningService: KlagebehandlingAvslutningService,
    private val vedtakJournalfoeringService: VedtakJournalfoeringService,
    private val kabalDocumentGateway: KabalDocumentGateway
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        const val SYSTEMBRUKER = "SYSTEMBRUKER" //TODO ??
        const val SYSTEM_JOURNALFOERENDE_ENHET = "9999"
    }

    @Transactional(propagation = Propagation.NEVER)
    fun distribuerKlagebehandling(klagebehandlingId: UUID) {
        try {
            var klagebehandling =
                klagebehandlingService.getKlagebehandlingForUpdateBySystembruker(klagebehandlingId)

            if (klagebehandling.getVedtakOrException().dokumentEnhetId != null) {
                klagebehandling = lagBrevmottakere(klagebehandling, klagebehandling.getVedtakOrException().id)
                val fullfoert =
                    kabalDocumentGateway.fullfoerDokumentEnhet(klagebehandling.getVedtakOrException().dokumentEnhetId!!)
                if (fullfoert) {
                    if (klagebehandling.getVedtakOrException().mellomlagerId != null) {
                        slettMellomlagretDokument(klagebehandling.id, klagebehandling.getVedtakOrException().id)
                    }
                    avsluttKlagebehandling(klagebehandling.id)
                }
            } else {

                if (klagebehandling.getVedtakOrException().erIkkeFerdigDistribuert()) {
                    klagebehandling.getVedtakOrException().let { vedtak ->
                        logger.debug("Vedtak ${vedtak.id} i klagebehandling $klagebehandlingId er ikke distribuert")

                        klagebehandling = lagBrevmottakere(klagebehandling, vedtak.id)
                        val brevmottakere = klagebehandling.getVedtakOrException().brevmottakere

                        brevmottakere
                            .filter { brevMottaker -> brevMottaker.erIkkeDistribuertTil() }
                            .forEach { brevMottaker ->
                                logger.debug("Vedtak ${vedtak.id} i klagebehandling $klagebehandlingId er ikke distribuert til brevmottaker ${brevMottaker.id}")

                                opprettJournalpostForBrevMottaker(klagebehandling.id, brevMottaker.id)
                                ferdigstillJournalpostForBrevMottaker(klagebehandling.id, brevMottaker.id)
                                distribuerVedtakTilBrevmottaker(klagebehandling.id, brevMottaker.id)
                            }

                        slettMellomlagretDokument(klagebehandling.id, vedtak.id)

                        markerVedtakSomFerdigDistribuert(klagebehandling.id, vedtak.id)
                    }
                    avsluttKlagebehandling(klagebehandling.id)
                }
            }
        } catch (e: Exception) {
            logger.error("Feilet under distribuering av klagebehandling $klagebehandlingId", e)
        }
    }

    private fun lagBrevmottakere(
        klagebehandling: Klagebehandling,
        vedtakId: UUID
    ): Klagebehandling {
        val vedtak = klagebehandling.getVedtakOrException()
        if (vedtak.harIngenBrevMottakere()) {
            logger.debug("Vedtak $vedtakId i klagebehandling ${klagebehandling.id} har ingen brevmottakere, vi oppretter det")
            return vedtakDistribusjonService.lagBrevmottakere(klagebehandling.id, vedtakId)
        }
        return klagebehandling
    }

    private fun opprettJournalpostForBrevMottaker(
        klagebehandlingId: UUID,
        brevMottakerId: UUID
    ): Klagebehandling {
        return vedtakJournalfoeringService.opprettJournalpostForBrevMottaker(
            klagebehandlingId,
            brevMottakerId
        )
    }

    private fun ferdigstillJournalpostForBrevMottaker(
        klagebehandlingId: UUID,
        brevMottakerId: UUID
    ): Klagebehandling {
        return vedtakJournalfoeringService.ferdigstillJournalpostForBrevMottaker(
            klagebehandlingId,
            brevMottakerId
        )
    }

    private fun distribuerVedtakTilBrevmottaker(
        klagebehandlingId: UUID,
        brevMottakerId: UUID
    ): Klagebehandling {
        logger.debug("Distribuerer vedtak i klagebehandling $klagebehandlingId til brevmottaker $brevMottakerId")
        return vedtakDistribusjonService.distribuerJournalpostTilMottaker(
            klagebehandlingId, brevMottakerId
        )
    }

    fun slettMellomlagretDokument(
        klagebehandlingId: UUID,
        vedtakId: UUID,
    ): Klagebehandling {
        logger.debug("Sletter mellomlagret fil i vedtak $vedtakId i klagebehandling${klagebehandlingId}")
        return vedtakDistribusjonService.slettMellomlagretDokument(
            klagebehandlingId,
            vedtakId
        )
    }

    private fun markerVedtakSomFerdigDistribuert(
        klagebehandlingId: UUID,
        vedtakId: UUID
    ): Klagebehandling {
        logger.debug("Markerer vedtak $vedtakId i klagebehandling $klagebehandlingId som ferdig distribuert")
        return vedtakDistribusjonService.markerVedtakSomFerdigDistribuert(klagebehandlingId, vedtakId)
    }

    private fun avsluttKlagebehandling(klagebehandlingId: UUID) {
        logger.debug("Alle vedtak i klagebehandling $klagebehandlingId er ferdig distribuert, så vi markerer klagebehandlingen som avsluttet")
        klagebehandlingAvslutningService.avsluttKlagebehandling(klagebehandlingId)
    }
}




