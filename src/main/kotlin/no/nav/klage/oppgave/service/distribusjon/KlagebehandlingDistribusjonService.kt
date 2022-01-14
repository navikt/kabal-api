package no.nav.klage.oppgave.service.distribusjon

import no.nav.klage.oppgave.clients.kabaldocument.KabalDocumentGateway
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.service.VedtakService
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
    private val klagebehandlingAvslutningService: KlagebehandlingAvslutningService,
    private val kabalDocumentGateway: KabalDocumentGateway,
    private val vedtakService: VedtakService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
        const val SYSTEMBRUKER = "SYSTEMBRUKER"
    }

    @Transactional(propagation = Propagation.NEVER)
    fun distribuerKlagebehandling(klagebehandlingId: UUID) {
        try {
            val klagebehandling =
                klagebehandlingService.getKlagebehandlingForUpdateBySystembruker(klagebehandlingId)

            logger.debug("Distribuerer dokument med dokumentEnhetId ${klagebehandling.vedtak.dokumentEnhetId!!} for klagebehandling ${klagebehandling.id}")
            try {
                val hovedadressatJournalpostId = kabalDocumentGateway.fullfoerDokumentEnhet(klagebehandling.vedtak.dokumentEnhetId!!)

                vedtakService.addHovedadressatJournalpostId(
                    klagebehandlingId = klagebehandlingId,
                    utfoerendeSaksbehandlerIdent =  SYSTEMBRUKER,
                    journalpostId =  hovedadressatJournalpostId
                )

                logger.debug("Distribuerte dokument med dokumentEnhetId ${klagebehandling.vedtak.dokumentEnhetId!!} for klagebehandling ${klagebehandling.id}")
                avsluttKlagebehandling(klagebehandling.id)
            } catch (e: Exception) {
                logger.error(
                    "Fikk ikke distribuert dokument med dokumentEnhetId ${klagebehandling.vedtak.dokumentEnhetId!!} for klagebehandling ${klagebehandling.id}",
                    e
                )
            }

        } catch (e: Exception) {
            logger.error("Feilet under distribuering av klagebehandling $klagebehandlingId", e)
        }
    }

    private fun avsluttKlagebehandling(klagebehandlingId: UUID) {
        logger.debug("Alle vedtak i klagebehandling $klagebehandlingId er ferdig distribuert, så vi markerer klagebehandlingen som avsluttet")
        klagebehandlingAvslutningService.avsluttKlagebehandling(klagebehandlingId)
    }
}




