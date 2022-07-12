package no.nav.klage.oppgave.clients.events

import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import reactor.core.publisher.ConnectableFlux
import reactor.kafka.receiver.KafkaReceiver
import javax.annotation.PostConstruct

@Service
class KafkaEventClient(
    private val kafkaReceiver: KafkaReceiver<String, String>
) {

    private lateinit var eventPublisher: ConnectableFlux<ServerSentEvent<String>>

    @PostConstruct
    fun init() {
        eventPublisher = kafkaReceiver.receive()
            .map { consumerRecord -> ServerSentEvent.builder(consumerRecord.value()).build() }
            .publish()

        // subscribes to the KafkaReceiver -> starts consumption (without observers attached)
        eventPublisher.connect()
    }

    fun getEventPublisher() = eventPublisher
}