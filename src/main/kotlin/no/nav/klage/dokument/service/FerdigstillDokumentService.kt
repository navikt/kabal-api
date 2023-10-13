package no.nav.klage.dokument.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.dokument.domain.dokumenterunderarbeid.DokumentUnderArbeidAsHoveddokument
import no.nav.klage.oppgave.domain.events.DokumentFerdigstiltAvSaksbehandler
import no.nav.klage.oppgave.domain.kafka.Event
import no.nav.klage.oppgave.service.KafkaInternalEventService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.hibernate.Hibernate
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Service
class FerdigstillDokumentService(
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val dokumentUnderArbeidCommonService: DokumentUnderArbeidCommonService,
    private val kafkaInternalEventService: KafkaInternalEventService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Scheduled(fixedDelayString = "\${FERDIGSTILLE_DOKUMENTER_DELAY_MILLIS}", initialDelay = 45000)
    @SchedulerLock(name = "ferdigstillDokumenter")
    fun ferdigstillHovedDokumenter() {
        val hovedDokumenterIkkeFerdigstilte = dokumentUnderArbeidCommonService.findHoveddokumenterByMarkertFerdigNotNullAndFerdigstiltNull()
        for (it in hovedDokumenterIkkeFerdigstilte) {
            ferdigstill(it)
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @SchedulerLock(name = "ferdigstillDokumenter")
    fun listenToFerdigstilteDokumenterAvSaksbehandler(dokumentFerdigstiltAvSaksbehandler: DokumentFerdigstiltAvSaksbehandler) {
        logger.debug("listenToFerdigstilteDokumenterAvSaksbehandler called")
        val dua = Hibernate.unproxy(dokumentFerdigstiltAvSaksbehandler.dokumentUnderArbeid) as DokumentUnderArbeidAsHoveddokument
        ferdigstill(dua)
    }

    private fun ferdigstill(it: DokumentUnderArbeidAsHoveddokument) {
        logger.debug("ferdigstill hoveddokument with id {}", it.id)
        var updatedDokument = it
        try {
            if (updatedDokument.dokumentEnhetId == null) {
                updatedDokument = dokumentUnderArbeidService.opprettDokumentEnhet(updatedDokument.id)
            }
            updatedDokument = dokumentUnderArbeidService.ferdigstillDokumentEnhet(updatedDokument.id)

            logger.debug("dokumentUnderArbeidService.ferdigstillDokumentEnhet(updatedDokument.id) for document with id {} done", updatedDokument.id)


            logger.debug("about to publish 'finished' to Kafak for document with id {}", updatedDokument.id)
            //Send to all subscribers. If this fails, it's not the end of the world.
            kafkaInternalEventService.publishEvent(
                Event(
                    behandlingId = updatedDokument.behandlingId.toString(),
                    name = "finished",
                    id = updatedDokument.id.toString(),
                    data = updatedDokument.id.toString(),
                )
            )

        } catch (e: Exception) {
            logger.error("Could not 'ferdigstillHovedDokumenter' with dokumentEnhetId: ${updatedDokument.dokumentEnhetId}. See secure logs.")
            secureLogger.error(
                "Could not 'ferdigstillHovedDokumenter' with dokumentEnhetId: ${updatedDokument.dokumentEnhetId}",
                e
            )
        }
        logger.debug("ferdigstill for document with id {} successful", updatedDokument.id)
    }
}