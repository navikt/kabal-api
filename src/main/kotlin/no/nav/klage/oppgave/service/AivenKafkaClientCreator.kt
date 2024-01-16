package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.kafka.receiver.KafkaReceiver
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.receiver.internals.ConsumerFactory
import reactor.kafka.receiver.internals.DefaultKafkaReceiver
import java.util.*


@Service
class AivenKafkaClientCreator(
    @Value("\${KAFKA_BROKERS}")
    private val kafkaBrokers: String,
    @Value("\${KAFKA_TRUSTSTORE_PATH}")
    private val kafkaTruststorePath: String,
    @Value("\${KAFKA_CREDSTORE_PASSWORD}")
    private val kafkaCredstorePassword: String,
    @Value("\${KAFKA_KEYSTORE_PATH}")
    private val kafkaKeystorePath: String,
    @Value("\${INTERNAL_BEHANDLING_EVENT_TOPIC}")
    private val internalBehandlingEventTopicV1: String,
    @Value("\${INTERNAL_IDENTITY_EVENT_TOPIC}")
    private val internalIdentityEventTopicV1: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun getNewKafkaInternalBehandlingEventReceiver(): KafkaReceiver<String, String> {
        return defaultKafkaReceiver(topic = internalBehandlingEventTopicV1, eventType = "behandling")
    }

    fun getNewKafkaInternalIdentityEventReceiver(): KafkaReceiver<String, String> {
        return defaultKafkaReceiver(topic = internalIdentityEventTopicV1, eventType = "identity")
    }

    private fun defaultKafkaReceiver(topic: String, eventType: String): DefaultKafkaReceiver<String, String> {
        val uniqueIdPerInstance = UUID.randomUUID().toString()
        val config = mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to "kabal-api-$eventType-event-consumer-$uniqueIdPerInstance",
            ConsumerConfig.CLIENT_ID_CONFIG to "kabal-api-$eventType-event-client-$uniqueIdPerInstance",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to true,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ) + commonConfig()

        return DefaultKafkaReceiver(
            ConsumerFactory.INSTANCE,
            ReceiverOptions.create<String, String>(config).subscription(listOf(topic))
        )
    }

    //Common
    private fun commonConfig() = mapOf(
        BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers
    ) + securityConfig()

    private fun securityConfig() = mapOf(
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