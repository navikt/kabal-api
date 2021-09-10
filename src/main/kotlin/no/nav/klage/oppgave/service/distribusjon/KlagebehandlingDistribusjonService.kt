package no.nav.klage.oppgave.service.distribusjon

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
    private val vedtakJournalfoeringService: VedtakJournalfoeringService
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
                klagebehandlingService.getKlagebehandlingForUpdateBySystembruker(klagebehandlingId, null)
            klagebehandling.vedtak
                .filter { vedtak -> vedtak.erIkkeFerdigDistribuert() }
                .forEach { vedtak ->
                    logger.debug("Vedtak ${vedtak.id} i klagebehandling $klagebehandlingId er ikke distribuert")

                    klagebehandling = lagBrevmottakere(klagebehandling, vedtak.id)
                    val brevmottakere = klagebehandling.getVedtak(vedtak.id).brevmottakere
                    brevmottakere
                        .filter { brevMottaker -> brevMottaker.erIkkeDistribuertTil() }
                        .forEach { brevMottaker ->
                            logger.debug("Vedtak ${vedtak.id} i klagebehandling $klagebehandlingId er ikke distribuert til brevmottaker ${brevMottaker.id}")

                            opprettJournalpostForBrevMottaker(klagebehandling, vedtak.id, brevMottaker.id)
                            ferdigstillJournalpostForBrevMottaker(klagebehandling, vedtak.id, brevMottaker.id)
                            distribuerVedtakTilBrevmottaker(klagebehandling, vedtak.id, brevMottaker.id)
                        }

                    slettMellomlagretDokument(klagebehandling, vedtak.id)

                    markerVedtakSomFerdigDistribuert(klagebehandling, vedtak.id)
                }

            avsluttKlagebehandling(klagebehandling)
        } catch (e: Exception) {
            logger.error("Feilet under distribuering av klagebehandling $klagebehandlingId", e)
        }
    }

    private fun lagBrevmottakere(
        klagebehandling: Klagebehandling,
        vedtakId: UUID
    ): Klagebehandling {
        val vedtak = klagebehandling.getVedtak(vedtakId)
        if (vedtak.harIngenBrevMottakere()) {
            logger.debug("Vedtak $vedtakId i klagebehandling ${klagebehandling.id} har ingen brevmottakere, vi oppretter det")
            return vedtakDistribusjonService.lagBrevmottakere(klagebehandling.id, vedtakId)
        }
        return klagebehandling
    }

    private fun opprettJournalpostForBrevMottaker(
        klagebehandling: Klagebehandling,
        vedtakId: UUID,
        brevMottakerId: UUID
    ): Klagebehandling {
        return vedtakJournalfoeringService.opprettJournalpostForBrevMottaker(
            klagebehandling.id,
            vedtakId,
            brevMottakerId
        )
    }

    private fun ferdigstillJournalpostForBrevMottaker(
        klagebehandling: Klagebehandling,
        vedtakId: UUID,
        brevMottakerId: UUID
    ): Klagebehandling {
        return vedtakJournalfoeringService.ferdigstillJournalpostForBrevMottaker(
            klagebehandling.id,
            vedtakId,
            brevMottakerId
        )
    }

    private fun distribuerVedtakTilBrevmottaker(
        klagebehandling: Klagebehandling,
        vedtakId: UUID,
        brevMottakerId: UUID
    ): Klagebehandling {
        val vedtak = klagebehandling.getVedtak(vedtakId)
        val brevMottaker = vedtak.getMottaker(brevMottakerId)
        logger.debug("Distribuerer vedtak ${vedtakId} i klagebehandling ${klagebehandling.id} til brevmottaker ${brevMottaker.id}")
        return vedtakDistribusjonService.distribuerJournalpostTilMottaker(
            klagebehandling.id, vedtak.id, brevMottaker.id
        )
    }

    fun slettMellomlagretDokument(
        klagebehandling: Klagebehandling,
        vedtakId: UUID,
    ): Klagebehandling {
        logger.debug("Sletter mellomlagret fil i vedtak ${vedtakId} i klagebehandling${klagebehandling.id}")
        return vedtakDistribusjonService.slettMellomlagretDokument(
            klagebehandling.id,
            vedtakId
        )
    }

    private fun markerVedtakSomFerdigDistribuert(
        klagebehandling: Klagebehandling,
        vedtakId: UUID
    ): Klagebehandling {
        val vedtak = klagebehandling.getVedtak(vedtakId)
        logger.debug("Markerer vedtak ${vedtak.id} i klagebehandling ${klagebehandling.id} som ferdig distribuert")
        return vedtakDistribusjonService.markerVedtakSomFerdigDistribuert(klagebehandling.id, vedtak.id)
    }

    private fun avsluttKlagebehandling(klagebehandling: Klagebehandling) {
        logger.debug("Alle vedtak i klagebehandling ${klagebehandling.id} er ferdig distribuert, så vi markerer klagebehandlingen som avsluttet")
        klagebehandlingAvslutningService.avsluttKlagebehandling(klagebehandling.id)
    }
}




