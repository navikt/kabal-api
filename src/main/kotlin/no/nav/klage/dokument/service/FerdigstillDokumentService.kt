package no.nav.klage.dokument.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.dokument.domain.Event
import no.nav.klage.dokument.repositories.DokumentUnderArbeidRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class FerdigstillDokumentService(
    private val dokumentUnderArbeidService: DokumentUnderArbeidService,
    private val dokumentUnderArbeidRepository: DokumentUnderArbeidRepository,
    private val aivenKafkaTemplate: KafkaTemplate<String, String>,
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
                runCatching {
                    val event = Event(
                        name = "finished",
                        id = it.id.id.toString(),
                        data = it.id.id.toString(),
                    )
                    logger.debug("Publishing document event to Kafka for subscribers: {}", event)

                    val result = aivenKafkaTemplate.send(
                        "klage.internal-events.v1",
                        jacksonObjectMapper().writeValueAsString(event)
                    ).get()
                    logger.debug("Published document event to Kafka for subscribers: {}", result)
                }.onFailure {
                    logger.error("Could not publish document event to subscribers", it)
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