package no.nav.klage.oppgave.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.klage.oppgave.domain.kafka.InternalBehandlingEvent
import no.nav.klage.oppgave.domain.kafka.InternalIdentityEvent
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaInternalEventService(
    private val aivenKafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${INTERNAL_BEHANDLING_EVENT_TOPIC}")
    private val internalBehandlingEventTopic: String,
    @Value("\${INTERNAL_IDENTITY_EVENT_TOPIC}")
    private val internalIdentityEventTopic: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun publishInternalBehandlingEvent(internalBehandlingEvent: InternalBehandlingEvent) {
        runCatching {
            logger.debug("Publishing internalBehandlingEvent to Kafka for subscribers: {}", internalBehandlingEvent)

            val result = aivenKafkaTemplate.send(
                internalBehandlingEventTopic,
                jacksonObjectMapper().writeValueAsString(internalBehandlingEvent)
            ).get()
            logger.debug("Published internalBehandlingEvent to Kafka for subscribers: {}", result)
        }.onFailure {
            logger.error("Could not publish internalBehandlingEvent to subscribers", it)
        }
    }

    fun publishInternalIdentityEvent(internalIdentityEvent: InternalIdentityEvent) {
        runCatching {
            logger.debug("Publishing internalIdentityEvent to Kafka for subscribers: {}", internalIdentityEvent)

            val result = aivenKafkaTemplate.send(
                internalIdentityEventTopic,
                jacksonObjectMapper().writeValueAsString(internalIdentityEvent)
            ).get()
            logger.debug("Published internalIdentityEvent to Kafka for subscribers: {}", result)
        }.onFailure {
            logger.error("Could not publish internalIdentityEvent to subscribers", it)
        }
    }
}