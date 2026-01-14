package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.domain.kafka.InternalBehandlingEvent
import no.nav.klage.oppgave.domain.kafka.InternalIdentityEvent
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.*

@Service
class KafkaInternalEventService(
    private val aivenKafkaTemplate: KafkaTemplate<String, String>,
    @Value($$"${INTERNAL_BEHANDLING_EVENT_TOPIC}")
    private val internalBehandlingEventTopic: String,
    @Value($$"${INTERNAL_IDENTITY_EVENT_TOPIC}")
    private val internalIdentityEventTopic: String,
    @Value($$"${NOTIFICATION_EVENT_TOPIC}")
    private val notificationEventTopic: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val jacksonObjectMapper = jacksonObjectMapper()
    }

    fun publishInternalBehandlingEvent(internalBehandlingEvent: InternalBehandlingEvent) {
        runCatching {
            logger.debug("Publishing internalBehandlingEvent to Kafka for subscribers")

            aivenKafkaTemplate.send(
                internalBehandlingEventTopic,
                jacksonObjectMapper.writeValueAsString(internalBehandlingEvent)
            ).get()
            logger.debug("Published internalBehandlingEvent to Kafka for subscribers")
        }.onFailure {
            logger.error("Could not publish internalBehandlingEvent to subscribers", it)
        }
    }

    fun publishInternalIdentityEvent(internalIdentityEvent: InternalIdentityEvent) {
        runCatching {
            logger.debug("Publishing internalIdentityEvent to Kafka for subscribers")

            aivenKafkaTemplate.send(
                internalIdentityEventTopic,
                jacksonObjectMapper.writeValueAsString(internalIdentityEvent)
            ).get()
            logger.debug("Published internalIdentityEvent to Kafka for subscribers")
        }.onFailure {
            logger.error("Could not publish internalIdentityEvent to subscribers", it)
        }
    }

    fun publishNotificationEvent(id: UUID, jsonNode: JsonNode) {
        runCatching {
            logger.debug("Publishing notificationEvent to Kafka for subscribers")

            aivenKafkaTemplate.send(
                notificationEventTopic,
                id.toString(),
                jacksonObjectMapper.writeValueAsString(jsonNode)
            ).get()
            logger.debug("Published notificationEvent to Kafka for subscribers")
        }.onFailure {
            logger.error("Could not publish notificationEvent to subscribers", it)
        }
    }
}