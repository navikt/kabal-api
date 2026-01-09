package no.nav.klage.oppgave.service

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.stereotype.Component
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

const val TEST_TOPIC = "test-topic"

@Disabled
@SpringBootTest(
    classes = [
        KafkaProducer::class,
        KafkaAutoConfiguration::class,
        KafkaConsumer::class
    ]
)
@ActiveProfiles("local")
@DirtiesContext
@EmbeddedKafka(partitions = 1, brokerProperties = ["listeners=PLAINTEXT://localhost:9092", "port=9092"])
class KafkaProducerTest {

    @Autowired
    lateinit var kafkaProducer: KafkaProducer

    @Autowired
    lateinit var kafkaConsumer: KafkaConsumer

    @Test
    fun `payload sent properly`() {
        kafkaProducer.publishToKafkaTopic(
            UUID.randomUUID(), "{}", TEST_TOPIC
        )
        kafkaConsumer.latch.await(1000, TimeUnit.MILLISECONDS)

        assertThat(kafkaConsumer.payload?.contains("Testref") ?: false)
    }

}

@Component
class KafkaConsumer {
    val latch = CountDownLatch(1)
    var payload: String? = null

    @KafkaListener(topics = [TEST_TOPIC])
    fun receive(consumerRecord: ConsumerRecord<*, *>) {
        println("received payload='${consumerRecord}'")
        payload = consumerRecord.toString()
        latch.countDown()
    }
}
