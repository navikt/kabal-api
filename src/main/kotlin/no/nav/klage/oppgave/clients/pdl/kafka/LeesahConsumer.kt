package no.nav.klage.oppgave.clients.pdl.kafka

import no.nav.klage.oppgave.clients.pdl.PersonCacheService
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.PersonService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.event.EventListener
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.event.ListenerContainerIdleEvent
import org.springframework.stereotype.Component
import java.net.InetAddress

@Component
class LeesahConsumer(
    private val personCacheService: PersonCacheService,
    private val personService: PersonService,
    private val behandlingService: BehandlingService
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    @KafkaListener(
        id = "kabalApiLeesahListener",
//        idIsGroup = false,
        containerFactory = "leesahKafkaListenerContainerFactory",
        topics = ["\${LEESAH_KAFKA_TOPIC}"],
    )
    fun listen(
        cr: ConsumerRecord<String, GenericRecord>,
    ) {
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
                logger.debug("Personhendelse is relevant for our cache in pod ${InetAddress.getLocalHost().hostName}. Updating person in cache.")
                personService.refreshPersonInCache(fnr = personhendelse.fnr)
                if (personhendelse.isAdressebeskyttelse) {
                    logger.debug("Adressebeskyttelse change for person in cache, updating index in kabal-search.")
                    behandlingService.indexAllBehandlingerForSakenGjelderFnr(sakenGjelderFnr = fnrInPersonhendelse)
                }
            }
        }
    }

    var kafkaConsumerIdleAfterStartup = false

    @EventListener(condition = "event.listenerId.startsWith('kabalApiLeesahListener-')")
    fun eventHandler(event: ListenerContainerIdleEvent) {
        if (!kafkaConsumerIdleAfterStartup) {
            logger.debug("Mottok ListenerContainerIdleEvent fra kabalApiLeesahListener in pod ${InetAddress.getLocalHost().hostName}.")
            personService.fillCacheWithAllMissingPersons()
        }
        kafkaConsumerIdleAfterStartup = true
    }
}