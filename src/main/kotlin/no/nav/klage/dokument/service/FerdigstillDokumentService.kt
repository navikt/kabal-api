package no.nav.klage.dokument.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.dokument.domain.Event
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderRecord

@Service
class FerdigstillDokumentService(
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val kafkaEventSender: KafkaSender<String, String>,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Scheduled(fixedDelayString = "\${FERDIGSTILLE_DOKUMENTER_DELAY_MILLIS}", initialDelay = 45000)
    @SchedulerLock(name = "ferdigstillDokumenter")
    fun ferdigstillHovedDokumenter() {
        val hovedDokumenterIkkeFerdigstilte =
            dokumentUnderArbeidRepository.findByMarkertFerdigNotNullAndFerdigstiltNullAndParentIdIsNull()
        for (it in hovedDokumenterIkkeFerdigstilte) {
            try {
                if (it.dokumentEnhetId == null) {
                    dokumentUnderArbeidService.opprettDokumentEnhet(it.id)
                }
                dokumentUnderArbeidService.ferdigstillDokumentEnhet(it.id)

                //Send to all subscribers. If this fails, it's not the end of the world.
                try {
                    val event = Event(
                        name = "finished",
                        id = it.id.id.toString(),
                        data = it.id.id.toString(),
                    )
                    logger.debug("sending event to subscribing clients: {}", event)
                    kafkaEventSender.send(
                        Mono.just<SenderRecord<String, String, String>>(
                            SenderRecord.create(
                                ProducerRecord(
                                    "klage.internal-events.v1",
                                    jacksonObjectMapper().writeValueAsString(
                                        event
                                    )
                                ), it.id.id.toString()
                            )
                        )
                    ).doOnError {
                        //is this triggered?
                        logger.error("Could not inform subscribers", it)
                    }.blockFirst()

                    logger.debug("event sent to subscribing clients")
                } catch (e: Exception) {
                    //or this?
                    logger.error("Caught exception. Could not inform subscribers", e)
                }

            } catch (e: Exception) {
                logger.error("Could not 'ferdigstillHovedDokumenter' with dokumentEnhetId: ${it.dokumentEnhetId}. See secure logs.")
                secureLogger.error(
                    "Could not 'ferdigstillHovedDokumenter' with dokumentEnhetId: ${it.dokumentEnhetId}",
                    e
                )
            }
        }
    }
}