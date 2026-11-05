package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.util.getLogger
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.UUID

/**
 * Each pod has one consumer per topic with a unique group id, so every pod receives every event.
 * Records are multicast to all connected SSE clients. Records arriving while no client is connected are dropped.
 */
@Component
class InternalKafkaEventStreams(
    @Value($$"${KAFKA_BROKERS}")
    private val kafkaBrokers: String,
    @Value($$"${KAFKA_TRUSTSTORE_PATH}")
    private val kafkaTruststorePath: String,
    @Value($$"${KAFKA_CREDSTORE_PASSWORD}")
    private val kafkaCredstorePassword: String,
    @Value($$"${KAFKA_KEYSTORE_PATH}")
    private val kafkaKeystorePath: String,
    @Value($$"${INTERNAL_BEHANDLING_EVENT_TOPIC}")
    internalBehandlingEventTopic: String,
    @Value($$"${INTERNAL_IDENTITY_EVENT_TOPIC}")
    internalIdentityEventTopic: String,
) : SmartLifecycle {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val uniqueIdPerInstance = UUID.randomUUID().toString()
    }

    private val behandlingEventsSink = Sinks.many().multicast().directBestEffort<ConsumerRecord<String, String>>()
    private val identityEventsSink = Sinks.many().multicast().directBestEffort<ConsumerRecord<String, String>>()

    private val behandlingEventsContainer =
        createContainer(
            topic = internalBehandlingEventTopic,
            eventType = "behandling",
            sink = behandlingEventsSink,
        )
    private val identityEventsContainer =
        createContainer(
            topic = internalIdentityEventTopic,
            eventType = "identity",
            sink = identityEventsSink,
        )

    @Volatile
    private var running = false

    fun behandlingEvents(): Flux<ConsumerRecord<String, String>> = behandlingEventsSink.asFlux()

    fun identityEvents(): Flux<ConsumerRecord<String, String>> = identityEventsSink.asFlux()

    override fun start() {
        behandlingEventsContainer.start()
        identityEventsContainer.start()
        running = true
    }

    override fun stop() {
        behandlingEventsContainer.stop()
        identityEventsContainer.stop()
        behandlingEventsSink.tryEmitComplete()
        identityEventsSink.tryEmitComplete()
        running = false
    }

    override fun isRunning(): Boolean = running

    private fun createContainer(
        topic: String,
        eventType: String,
        sink: Sinks.Many<ConsumerRecord<String, String>>,
    ): KafkaMessageListenerContainer<String, String> {
        val config =
            mapOf(
                ConsumerConfig.GROUP_ID_CONFIG to "kabal-api-$eventType-event-consumer-$uniqueIdPerInstance",
                ConsumerConfig.CLIENT_ID_CONFIG to "kabal-api-$eventType-event-client-$uniqueIdPerInstance",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ) + commonConfig()

        val containerProperties = ContainerProperties(topic)
        containerProperties.setMessageListener(MessageListener<String, String> { record -> emit(sink = sink, record = record) })
        containerProperties.ackMode = ContainerProperties.AckMode.BATCH
        val consumerFactory = DefaultKafkaConsumerFactory<String, String>(config)
        return KafkaMessageListenerContainer(consumerFactory, containerProperties)
    }

    private fun emit(
        sink: Sinks.Many<ConsumerRecord<String, String>>,
        record: ConsumerRecord<String, String>,
    ) {
        // Never log the record value: identity events contain fødselsnummer.
        val result =
            try {
                sink.tryEmitNext(record)
            } catch (e: Exception) {
                logger.warn(
                    "Could not emit Kafka record from topic {} partition {} offset {}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    e,
                )
                return
            }

        if (result != Sinks.EmitResult.OK && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            logger.warn(
                "Could not emit Kafka record from topic {} partition {} offset {}: {}",
                record.topic(),
                record.partition(),
                record.offset(),
                result,
            )
        }
    }

    private fun commonConfig() =
        mapOf(
            BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
        ) + securityConfig()

    private fun securityConfig() =
        mapOf(
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SSL",
            SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "", // Disable server host name verification
            SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "JKS",
            SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
            SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to kafkaTruststorePath,
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
            SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to kafkaKeystorePath,
            SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
            SslConfigs.SSL_KEY_PASSWORD_CONFIG to kafkaCredstorePassword,
        )
}
