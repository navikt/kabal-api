package no.nav.klage.oppgave.api.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.annotations.Api
import no.nav.klage.dokument.domain.Event
import no.nav.klage.oppgave.clients.events.KafkaEventClient
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Duration

@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/behandlinger/{behandlingId}/events")
class EventController(
    private val kafkaEventClient: KafkaEventClient,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @PathVariable("behandlingId") behandlingId: String,
    ): Flux<ServerSentEvent<JsonNode>> {
        logger.debug("events called for behandlingId: {}", behandlingId)

        //https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#mvc-ann-async-disconnects
        val heartbeatStream: Flux<ServerSentEvent<JsonNode>> = Flux.interval(Duration.ofSeconds(10))
            .takeWhile { true }
            .map { tick -> toHeartBeatServerSentEvent(tick) }

        return kafkaEventClient.getEventPublisher()
            .mapNotNull { event -> jsonToEvent(event.data()) }
            .filter { it.behandlingId == behandlingId }
            .mapNotNull { eventToServerSentEvent(it) }
            .mergeWith(heartbeatStream)
    }

    private fun toHeartBeatServerSentEvent(tick: Long): ServerSentEvent<JsonNode> {
        return eventToServerSentEvent(
            Event(
                behandlingId = "",
                id = "",
                name = "heartbeat-event-$tick",
                data = "{}"
            )
        )
    }

    private fun eventToServerSentEvent(event: Event): ServerSentEvent<JsonNode> {
        return ServerSentEvent.builder<JsonNode>()
            .id(event.id)
            .event(event.name)
            .data(jacksonObjectMapper().readTree(event.data))
            .build()
    }

    private fun jsonToEvent(json: String?): Event {
        val event = jacksonObjectMapper().readValue(json, Event::class.java)
        logger.debug("Received event from Kafka: {}", event)
        return event
    }
}