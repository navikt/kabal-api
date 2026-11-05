package no.nav.klage.oppgave.api.controller

import io.micrometer.core.instrument.MeterRegistry
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.config.SseShutdownSignal
import no.nav.klage.oppgave.config.getGauge
import no.nav.klage.oppgave.domain.kafka.InternalBehandlingEvent
import no.nav.klage.oppgave.domain.kafka.InternalIdentityEvent
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InternalKafkaEventStreams
import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@RestController
@Tag(name = "kabal-api")
@RequestMapping("/behandlinger/{behandlingId}")
class EventController(
    private val internalKafkaEventStreams: InternalKafkaEventStreams,
    meterRegistry: MeterRegistry,
    private val behandlingService: BehandlingService,
    private val sseShutdownSignal: SseShutdownSignal,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        // The sink drops events for subscribers without demand. This per-client buffer queues events and errors on
        // overflow, so the client reconnects.
        private const val SSE_CLIENT_BUFFER_SIZE = 256

        private val objectMapper = jacksonObjectMapper()
    }

    private val gaugeBehandling =
        meterRegistry.getGauge(
            eventType = "behandling",
            currentCount = AtomicInteger(0),
        )

    private val gaugeIdentity =
        meterRegistry.getGauge(
            eventType = "identity",
            currentCount = AtomicInteger(0),
        )

    private val gaugeBehandlingHeartbeat =
        meterRegistry.getGauge(
            eventType = "behandling-heartbeat",
            currentCount = AtomicInteger(0),
        )

    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @PathVariable("behandlingId") behandlingId: UUID,
    ): Flux<ServerSentEvent<JsonNode>> {
        val behandlingView = behandlingService.getBehandlingDetaljerView(behandlingId = behandlingId)

        // https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#mvc-ann-async-disconnects
        val heartbeatStream: Flux<ServerSentEvent<JsonNode>> = getHeartbeatStream()

        val behandlingEventPublisher =
            getBehandlingEventPublisher(
                behandlingId = behandlingId,
            )

        val identityEventPublisher =
            getIdentityEventPublisher(
                behandlingView = behandlingView,
            )

        val emitFirstHeartbeat = getFirstHeartbeat()

        // The stream never ends on its own, so complete it when the application shuts down
        // (the client then reconnects, normally to another pod).
        return behandlingEventPublisher
            .mergeWith(emitFirstHeartbeat)
            .mergeWith(heartbeatStream)
            .mergeWith(identityEventPublisher)
            .takeUntilOther(sseShutdownSignal.asMono())
    }

    // Each record is parsed once per pod and shared by all SSE clients. Parsing failures are skipped here, so they
    // cannot terminate the clients' streams.
    private val sharedBehandlingEvents: Flux<ParsedEvent<InternalBehandlingEvent>> by lazy {
        internalKafkaEventStreams
            .behandlingEvents()
            .mapNotNull { consumerRecord ->
                runCatching {
                    val event = objectMapper.readValue(consumerRecord.value(), InternalBehandlingEvent::class.java)
                    ParsedEvent(offset = consumerRecord.offset(), event = event, data = objectMapper.readTree(event.data))
                }.onFailure {
                    logger.error(
                        "Could not parse internal behandling event at offset {}, skipping it",
                        consumerRecord.offset(),
                        it,
                    )
                }.getOrNull()
            }.share()
    }

    private val sharedIdentityEvents: Flux<ParsedEvent<InternalIdentityEvent>> by lazy {
        internalKafkaEventStreams
            .identityEvents()
            .mapNotNull { consumerRecord ->
                runCatching {
                    val event = objectMapper.readValue(consumerRecord.value(), InternalIdentityEvent::class.java)
                    ParsedEvent(offset = consumerRecord.offset(), event = event, data = objectMapper.readTree(event.data))
                }.onFailure {
                    logger.error(
                        "Could not parse internal identity event at offset {}, skipping it",
                        consumerRecord.offset(),
                        it,
                    )
                }.getOrNull()
            }.share()
    }

    private fun getBehandlingEventPublisher(behandlingId: UUID): Flux<ServerSentEvent<JsonNode>> {
        val flux =
            sharedBehandlingEvents
                .onBackpressureBuffer(SSE_CLIENT_BUFFER_SIZE)
                .filter { it.event.behandlingId == behandlingId.toString() }
                .map {
                    ServerSentEvent
                        .builder<JsonNode>()
                        .id(it.offset.toString())
                        .event(it.event.type.name)
                        .data(it.data)
                        .build()
                }.doOnSubscribe {
                    gaugeBehandling.incrementAndGet()
                }.doFinally { _ ->
                    gaugeBehandling.decrementAndGet()
                }

        return flux
    }

    private fun getIdentityEventPublisher(behandlingView: BehandlingDetaljerView): Flux<ServerSentEvent<JsonNode>> {
        val flux =
            sharedIdentityEvents
                .onBackpressureBuffer(SSE_CLIENT_BUFFER_SIZE)
                .filter { it.event.identifikator == behandlingView.sakenGjelder.identifikator }
                .map {
                    ServerSentEvent
                        .builder<JsonNode>()
                        .id(it.offset.toString())
                        .event(it.event.type.name)
                        .data(it.data)
                        .build()
                }.doOnSubscribe {
                    gaugeIdentity.incrementAndGet()
                }.doFinally { _ ->
                    gaugeIdentity.decrementAndGet()
                }

        return flux
    }

    private fun getFirstHeartbeat(): Flux<ServerSentEvent<JsonNode>> {
        val emitFirstHeartbeat =
            Flux.generate<ServerSentEvent<JsonNode>> {
                it.next(toHeartBeatServerSentEvent())
                it.complete()
            }
        return emitFirstHeartbeat
    }

    private fun getHeartbeatStream(): Flux<ServerSentEvent<JsonNode>> {
        val heartbeatStream: Flux<ServerSentEvent<JsonNode>> =
            Flux
                .interval(Duration.ofSeconds(10))
                .map {
                    toHeartBeatServerSentEvent()
                }.doOnSubscribe {
                    gaugeBehandlingHeartbeat.incrementAndGet()
                }.doFinally { _ ->
                    gaugeBehandlingHeartbeat.decrementAndGet()
                }

        return heartbeatStream
    }

    private fun toHeartBeatServerSentEvent(): ServerSentEvent<JsonNode> =
        ServerSentEvent
            .builder<JsonNode>()
            .event("HEARTBEAT")
            .build()

    private data class ParsedEvent<T>(
        val offset: Long,
        val event: T,
        val data: JsonNode,
    )
}
