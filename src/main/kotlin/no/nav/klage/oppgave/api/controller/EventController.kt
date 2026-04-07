package no.nav.klage.oppgave.api.controller

import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.config.getGauge
import no.nav.klage.oppgave.domain.kafka.InternalBehandlingEvent
import no.nav.klage.oppgave.domain.kafka.InternalIdentityEvent
import no.nav.klage.oppgave.service.AivenKafkaClientCreator
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/behandlinger/{behandlingId}")
class EventController(
    private val aivenKafkaClientCreator: AivenKafkaClientCreator,
    meterRegistry: MeterRegistry,
    private val behandlingService: BehandlingService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    private val gaugeBehandling = meterRegistry.getGauge(
        eventType = "behandling",
        currentCount = AtomicInteger(0),
    )

    private val gaugeIdentity = meterRegistry.getGauge(
        eventType = "identity",
        currentCount = AtomicInteger(0),
    )

    private val gaugeBehandlingHeartbeat = meterRegistry.getGauge(
        eventType = "behandling-heartbeat",
        currentCount = AtomicInteger(0),
    )

    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(
        @PathVariable("behandlingId") behandlingId: UUID,
        @RequestParam("traceparent", required = false) traceparent: String?,
    ): Flux<ServerSentEvent<JsonNode>> {
        val otelContext = extractOtelContext(traceparent)
        val span = GlobalOpenTelemetry.getTracer("kabal-api")
            .spanBuilder("SSE /behandlinger/$behandlingId/events")
            .setParent(otelContext)
            .setSpanKind(SpanKind.SERVER)
            .startSpan()

        val behandlingView = behandlingService.getBehandlingDetaljerView(behandlingId = behandlingId)

        //https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#mvc-ann-async-disconnects
        val heartbeatStream: Flux<ServerSentEvent<JsonNode>> = getHeartbeatStream()

        val behandlingEventPublisher = getBehandlingEventPublisher(
            behandlingId = behandlingId,
        )

        val identityEventPublisher = getIdentityEventPublisher(
            behandlingView = behandlingView,
        )

        val emitFirstHeartbeat = getFirstHeartbeat()

        return behandlingEventPublisher
            .mergeWith(emitFirstHeartbeat)
            .mergeWith(heartbeatStream)
            .mergeWith(identityEventPublisher)
            .doFinally { span.end() }
            .contextWrite { ctx -> ctx.put("otel-context", span.storeInContext(otelContext)) }
    }

    private fun extractOtelContext(traceparent: String?): Context {
        if (traceparent == null) return Context.current()

        val carrier = mapOf("traceparent" to traceparent)
        return GlobalOpenTelemetry.getPropagators().textMapPropagator.extract(
            Context.current(),
            carrier,
            object : TextMapGetter<Map<String, String>> {
                override fun keys(carrier: Map<String, String>): Iterable<String> = carrier.keys
                override fun get(carrier: Map<String, String>?, key: String): String? = carrier?.get(key)
            }
        )
    }

    private fun getBehandlingEventPublisher(
        behandlingId: UUID,
    ): Flux<ServerSentEvent<JsonNode>> {
        val flux = aivenKafkaClientCreator.getNewKafkaInternalBehandlingEventReceiver().receive()
            .mapNotNull { consumerRecord ->
                val internalBehandlingEvent = jsonToInternalBehandlingEvent(consumerRecord.value())
                if (internalBehandlingEvent.behandlingId == behandlingId.toString()) {
                    ServerSentEvent.builder<JsonNode>()
                        .id(consumerRecord.offset().toString())
                        .event(internalBehandlingEvent.type.name)
                        .data(jacksonObjectMapper().readTree(internalBehandlingEvent.data))
                        .build()
                } else null
            }
            .doFinally { _ ->
                gaugeBehandling.decrementAndGet()
            }

        gaugeBehandling.incrementAndGet()

        return flux
    }

    private fun getIdentityEventPublisher(
        behandlingView: BehandlingDetaljerView,
    ): Flux<ServerSentEvent<JsonNode>> {
        val flux = aivenKafkaClientCreator.getNewKafkaInternalIdentityEventReceiver().receive()
            .mapNotNull { consumerRecord ->
                val internalIdentityEvent = jsonToInternalIdentityEvent(consumerRecord.value())
                if (internalIdentityEvent.identifikator == behandlingView.sakenGjelder.identifikator) {
                    ServerSentEvent.builder<JsonNode>()
                        .id(consumerRecord.offset().toString())
                        .event(internalIdentityEvent.type.name)
                        .data(jacksonObjectMapper().readTree(internalIdentityEvent.data))
                        .build()
                } else null
            }
            .doFinally { _ ->
                gaugeIdentity.decrementAndGet()
            }

        gaugeIdentity.incrementAndGet()

        return flux
    }

    private fun getFirstHeartbeat(): Flux<ServerSentEvent<JsonNode>> {
        val emitFirstHeartbeat = Flux.generate<ServerSentEvent<JsonNode>> {
            it.next(toHeartBeatServerSentEvent())
            it.complete()
        }
        return emitFirstHeartbeat
    }

    private fun getHeartbeatStream(
    ): Flux<ServerSentEvent<JsonNode>> {
        val heartbeatStream: Flux<ServerSentEvent<JsonNode>> = Flux.interval(Duration.ofSeconds(10))
            .map {
                toHeartBeatServerSentEvent()
            }
            .doFinally { _ ->
                gaugeBehandlingHeartbeat.decrementAndGet()
            }

        gaugeBehandlingHeartbeat.incrementAndGet()

        return heartbeatStream
    }

    private fun toHeartBeatServerSentEvent(): ServerSentEvent<JsonNode> {
        return ServerSentEvent.builder<JsonNode>()
            .event("HEARTBEAT")
            .build()
    }

    private fun jsonToInternalBehandlingEvent(json: String?): InternalBehandlingEvent =
        jacksonObjectMapper().readValue(json, InternalBehandlingEvent::class.java)

    private fun jsonToInternalIdentityEvent(json: String?): InternalIdentityEvent =
        jacksonObjectMapper().readValue(json, InternalIdentityEvent::class.java)
}