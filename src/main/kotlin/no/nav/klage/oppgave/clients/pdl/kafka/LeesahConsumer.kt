package no.nav.klage.oppgave.clients.pdl.kafka

import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.pdl.PersonCacheService
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.PartitionOffset
import org.springframework.kafka.annotation.TopicPartition
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.net.InetAddress

@Component
class LeesahConsumer(
    private val personCacheService: PersonCacheService,
    private val pdlFacade: PdlFacade,
    private val behandlingService: BehandlingService
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
        acknowledgment: Acknowledgment,
    ) {
        logger.debug("Reading offset ${cr.offset()} from partition ${cr.partition()} on kafka topic ${cr.topic()}")
        if (cr.offset() < offset) {
            logger.debug("Lowest offset found in pod ${InetAddress.getLocalHost().hostName}: ${cr.offset()}. Updating offset to this value. Trekkspill")
            offset = cr.offset()
        }
        processPersonhendelse(
            personhendelse = cr.value(),
        )

//        acknowledgment.acknowledge()
    }

    fun processPersonhendelse(
        personhendelse: GenericRecord,
    ) {
        val fnrInPersonhendelse = personhendelse.fnr
        if (personCacheService.isCached(foedselsnr = fnrInPersonhendelse)) {
            logger.debug("Personhendelse for person in cache found. Checking if relevant.")
            logger.debug("Logging personhendelse in pod ${InetAddress.getLocalHost().hostName} : {}", personhendelse)
            if (personhendelse.isRelevantForOurCache) {
                logger.debug("Personhendelse is relevant for our cache in pod ${InetAddress.getLocalHost().hostName}. Updating person in cache.")
                personCacheService.removePersonFromCache(foedselsnr = personhendelse.fnr)
                pdlFacade.getPersonInfo(fnr = personhendelse.fnr)
                if (personhendelse.isAdressebeskyttelse) {
                    logger.debug("Adressebeskyttelse change for person in cache, updating index in kabal-search.")
                    behandlingService.indexAllBehandlingerForSakenGjelderFnr(sakenGjelderFnr = fnrInPersonhendelse)
                }
            }
        }
    }
}