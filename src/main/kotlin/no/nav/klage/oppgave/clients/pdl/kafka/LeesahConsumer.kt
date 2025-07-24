package no.nav.klage.oppgave.clients.pdl.kafka

import no.nav.klage.oppgave.clients.pdl.PersonCacheService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.event.EventListener
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.PartitionOffset
import org.springframework.kafka.annotation.TopicPartition
import org.springframework.kafka.event.ListenerContainerIdleEvent
import org.springframework.stereotype.Component
import java.net.InetAddress

@Component
class LeesahConsumer(
    private val personCacheService: PersonCacheService,
) {


    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    var offset = 99999999L

    @KafkaListener(
        id = "kabalApiLeesahListener",
        idIsGroup = false,
        containerFactory = "leesahKafkaListenerContainerFactory",
        topicPartitions = [TopicPartition(
            topic = "\${LEESAH_KAFKA_TOPIC}",
            partitions = ["#{@leesahPartitionFinder.partitions('\${LEESAH_KAFKA_TOPIC}')}"],
            partitionOffsets = [PartitionOffset(partition = "*", initialOffset = "0")]
        )]
    )
    fun listen(
        cr: ConsumerRecord<String, GenericRecord>,
    ) {
        if (cr.offset() < offset) {
            logger.debug("Personhendelse: Lowest offset found in pod ${InetAddress.getLocalHost().hostName}: ${cr.offset()}. Updating offset to this value.")
            offset = cr.offset()
        }
        processPersonhendelse(
            personhendelse = cr.value(),
        )
    }

    fun processPersonhendelse(
        personhendelse: GenericRecord,
    ) {
        val fnrInPersonhendelse = personhendelse.fnr
        if (personCacheService.isCached(foedselsnr = fnrInPersonhendelse)) {
            logger.debug("Personhendelse for person in cache found in pod ${InetAddress.getLocalHost().hostName}. Checking if relevant.")
            if (personhendelse.isRelevantForOurCache) {
                logger.debug("Personhendelse is relevant for our cache in pod ${InetAddress.getLocalHost().hostName}.")
            }
        }
    }

    var kafkaConsumerIdleAfterStartup = false

    @EventListener(condition = "event.listenerId.startsWith('kabalApiLeesahListener-')")
    fun eventHandler(event: ListenerContainerIdleEvent) {
        if (!kafkaConsumerIdleAfterStartup) {
            logger.debug("Mottok ListenerContainerIdleEvent fra kabalApiLeesahListener in pod ${InetAddress.getLocalHost().hostName}.")
            //Sett i gang fylling av cache
        }
        kafkaConsumerIdleAfterStartup = true
    }
}