package no.nav.klage.oppgave.config

import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import reactor.kafka.receiver.KafkaReceiver
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.receiver.internals.DefaultKafkaReceiver
import java.time.Duration
import java.util.*

@Configuration
class AivenKafkaConfiguration(
    @Value("\${KAFKA_BROKERS}")
    private val kafkaBrokers: String,
    @Value("\${KAFKA_TRUSTSTORE_PATH}")
    private val kafkaTruststorePath: String,
    @Value("\${KAFKA_CREDSTORE_PASSWORD}")
    private val kafkaCredstorePassword: String,
    @Value("\${KAFKA_KEYSTORE_PATH}")
    private val kafkaKeystorePath: String,
    @Value("\${INTERNAL_EVENT_TOPIC}")
    private val internalEventTopic: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    //Producer bean
    @Bean
    fun aivenKafkaTemplate(): KafkaTemplate<String, String> {
        val config = mapOf(
            ProducerConfig.CLIENT_ID_CONFIG to "kabal-api-producer",
            ProducerConfig.ACKS_CONFIG to "1",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ) + commonConfig()

        return KafkaTemplate(DefaultKafkaProducerFactory(config))
    }

    //Consumer beans
    @Bean
    fun kafkaEventReceiver(): KafkaReceiver<String, String> {
        val uniqueIdPerInstance = UUID.randomUUID().toString()
        val config = mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to "kabal-api-event-consumer-$uniqueIdPerInstance",
            ConsumerConfig.CLIENT_ID_CONFIG to "kabal-api-event-client-$uniqueIdPerInstance",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to true,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ) + commonConfig()

        return DefaultKafkaReceiver(
            reactor.kafka.receiver.internals.ConsumerFactory.INSTANCE,
            ReceiverOptions.create<String, String>(config).subscription(listOf(internalEventTopic))
        )
    }

    @Bean
    fun egenAnsattKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = egenAnsattConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        factory.containerProperties.idleEventInterval = 3000L
        factory.setErrorHandler { thrownException, data ->
            logger.error("Could not deserialize record. See secure logs for details.")
            secureLogger.error("Could not deserialize record: $data", thrownException)
        }

        //Retry consumer/listener even if authorization fails at first
        factory.setContainerCustomizer { container ->
            container.containerProperties.setAuthExceptionRetryInterval(Duration.ofSeconds(10L))
        }

        return factory
    }

    @Bean
    fun egenAnsattConsumerFactory(): ConsumerFactory<String, String> {
        return DefaultKafkaConsumerFactory(egenAnsattConsumerProps())
    }

    private fun egenAnsattConsumerProps(): Map<String, Any> {
        return mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to "kabal-api",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
            "spring.deserializer.key.delegate.class" to StringDeserializer::class.java,
            "spring.deserializer.value.delegate.class" to StringDeserializer::class.java
        ) + commonConfig()
    }

    @Bean
    fun egenAnsattFinder(): PartitionFinder<String, String> {
        return PartitionFinder(egenAnsattConsumerFactory())
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

class PartitionFinder<K, V>(private val consumerFactory: ConsumerFactory<K, V>) {
    fun partitions(topic: String): Array<String> {
        consumerFactory.createConsumer().use { consumer ->
            return consumer.partitionsFor(topic)
                .map { pi -> "" + pi.partition() }
                .toTypedArray()
        }
    }
}
