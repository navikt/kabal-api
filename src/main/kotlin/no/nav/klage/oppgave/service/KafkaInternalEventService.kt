package no.nav.klage.oppgave.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.klage.dokument.domain.Event
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaInternalEventService(
    private val aivenKafkaTemplate: KafkaTemplate<String, String>,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun publishEvent(event: Event) {
        runCatching {
            logger.debug("Publishing internal event to Kafka for subscribers: {}", event)

            val result = aivenKafkaTemplate.send(
                "klage.internal-events.v1",
                jacksonObjectMapper().writeValueAsString(event)
            ).get()
            logger.debug("Published internal event to Kafka for subscribers: {}", result)
        }.onFailure {
            logger.error("Could not publish internal event to subscribers", it)
        }
    }
}