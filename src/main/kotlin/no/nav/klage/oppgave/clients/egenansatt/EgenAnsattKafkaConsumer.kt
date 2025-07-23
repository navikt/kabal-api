package no.nav.klage.oppgave.clients.egenansatt

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.PartitionOffset
import org.springframework.kafka.annotation.TopicPartition
import org.springframework.stereotype.Component


@Component
class EgenAnsattKafkaConsumer(
    private val egenAnsattService: EgenAnsattService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
        private val mapper = ObjectMapper().registerModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        ).registerModule(JavaTimeModule())
    }

    @KafkaListener(
        id = "klageEgenAnsattListener",
        idIsGroup = false,
        containerFactory = "egenAnsattKafkaListenerContainerFactory",
        topicPartitions = [TopicPartition(
            topic = "\${EGENANSATT_KAFKA_TOPIC}",
            partitions = ["#{@egenAnsattPartitionFinder.partitions('\${EGENANSATT_KAFKA_TOPIC}')}"],
            partitionOffsets = [PartitionOffset(partition = "*", initialOffset = "0")]
        )]
    )
    fun listen(egenAnsattRecord: ConsumerRecord<String, String>) {
        runCatching {
//            logger.debug("Reading offset ${egenAnsattRecord.offset()} from partition ${egenAnsattRecord.partition()} on kafka topic ${egenAnsattRecord.topic()}")
            val foedselsnr = egenAnsattRecord.key()
            val egenAnsatt = egenAnsattRecord.value().toEgenAnsatt()
            egenAnsattService.oppdaterEgenAnsatt(foedselsnr, egenAnsatt)
        }.onFailure {
            teamLogger.error("Failed to process egenansatt record", it)
            throw RuntimeException("Could not process egenansatt record. See more details in team-logs.")
        }
    }

    private fun String.toEgenAnsatt() = mapper.readValue(this, EgenAnsatt::class.java)
}
