package no.nav.klage.oppgave.api.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.config.getGauge
import no.nav.klage.oppgave.domain.kafka.InternalBehandlingEvent
import no.nav.klage.oppgave.domain.kafka.InternalIdentityEvent
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.service.AivenKafkaClientCreator
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.LocalDateTime
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
        private val secureLogger = getSecureLogger()
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
        @RequestParam("test-id", required = false) testId: String? = null,
        request: HttpServletRequest,
    ): Flux<ServerSentEvent<JsonNode>?> {
        val listenerStarted = LocalDateTime.now()

        var traceId = "unknown"
        try {
            //[00-b37d69e0b7574e7c8ea89df62c9bab4c-ce0c8200a58042dd-00]
            traceId = request.getHeader("traceparent").split("-")[1]
        } catch (e: Exception) {
            logger.warn("could not extract traceId")
        }

        logger.debug(
            "events called with testId: {}, for behandlingId: {} and protocol: {}, with start time: {}, traceId: {}",
            testId,
            behandlingId,
            request.protocol,
            listenerStarted,
            traceId,
        )

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId = behandlingId)

        //https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#mvc-ann-async-disconnects
        val heartbeatStream: Flux<ServerSentEvent<JsonNode>> = getHeartbeatStream(
            behandlingId = behandlingId,
            testId = testId,
            listenerStartTime = listenerStarted,
            traceId = traceId,
        )

        val behandlingEventPublisher = getBehandlingEventPublisher(
            behandlingId = behandlingId,
            testId = testId,
            listenerStartTime = listenerStarted,
            traceId = traceId,
        )

        val identityEventPublisher = getIdentityEventPublisher(
            behandling = behandling,
            testId = testId,
            listenerStartTime = listenerStarted,
            traceId = traceId,
        )

        val emitFirstHeartbeat = getFirstHeartbeat(
            behandlingId = behandlingId,
            testId = testId,
            traceId = traceId,
        )

        return behandlingEventPublisher
            .mergeWith(emitFirstHeartbeat)
            .mergeWith(heartbeatStream)
            .mergeWith(identityEventPublisher)
    }

    private fun getBehandlingEventPublisher(
        behandlingId: UUID,
        testId: String?,
        listenerStartTime: LocalDateTime,
        traceId: String,
    ): Flux<ServerSentEvent<JsonNode>?> {
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
            .doOnCancel {
                logger.debug(
                    "behandling events cancel for testId: {}, behandlingId: {}, with start time: {}, traceId: {}",
                    testId,
                    behandlingId,
                    listenerStartTime,
                    traceId,
                )
            }
            .doOnTerminate {
                logger.debug(
                    "behandling events terminate for testId: {}, behandlingId: {}, with start time: {}, traceId: {}",
                    testId,
                    behandlingId,
                    listenerStartTime,
                    traceId,
                )
            }
            .doFinally { signalType ->
                logger.debug(
                    "behandling events closed for testId: {}, behandlingId: {}. SignalType: {}, with start time: {}, traceId: {}",
                    testId,
                    behandlingId,
                    signalType,
                    listenerStartTime,
                    traceId,
                )
                gaugeBehandling.decrementAndGet()
            }

        gaugeBehandling.incrementAndGet()

        return flux
    }

    private fun getIdentityEventPublisher(
        behandling: Behandling,
        testId: String?,
        listenerStartTime: LocalDateTime,
        traceId: String,
    ): Flux<ServerSentEvent<JsonNode>?> {
        val flux = aivenKafkaClientCreator.getNewKafkaInternalIdentityEventReceiver().receive()
            .mapNotNull { consumerRecord ->
                val internalIdentityEvent = jsonToInternalIdentityEvent(consumerRecord.value())
                if (internalIdentityEvent.identifikator == behandling.sakenGjelder.partId.value) {
                    ServerSentEvent.builder<JsonNode>()
                        .id(consumerRecord.offset().toString())
                        .event(internalIdentityEvent.type.name)
                        .data(jacksonObjectMapper().readTree(internalIdentityEvent.data))
                        .build()
                } else null
            }
            .doOnCancel {
                logger.debug(
                    "identity events cancel for testId: {}, behandlingId: {}, with start time: {}, traceId: {}",
                    testId,
                    behandling.id,
                    listenerStartTime,
                    traceId,
                )
            }
            .doOnTerminate {
                logger.debug(
                    "identity events terminate for testId: {}, behandlingId: {}, with start time: {}, traceId: {}",
                    testId,
                    behandling.id,
                    listenerStartTime,
                    traceId,
                )
            }
            .doFinally { signalType ->
                logger.debug(
                    "identity events closed for testId: {}, behandlingId: {}. SignalType: {}, with start time: {}, traceId: {}",
                    testId,
                    behandling.id,
                    signalType,
                    listenerStartTime,
                    traceId,
                )
                gaugeIdentity.decrementAndGet()
            }

        gaugeIdentity.incrementAndGet()

        return flux
    }

    private fun getFirstHeartbeat(
        behandlingId: UUID,
        testId: String?,
        traceId: String,
    ): Flux<ServerSentEvent<JsonNode>> {
        val emitFirstHeartbeat = Flux.generate<ServerSentEvent<JsonNode>> {
            it.next(toHeartBeatServerSentEvent())
            it.complete()
        }
            .doFinally { signalType ->
                logger.debug(
                    "emitFirstHeartbeat closed for testId: {}, behandlingId: {}. SignalType: {}, traceId: {}",
                    testId,
                    behandlingId,
                    signalType,
                    traceId,
                )
            }
            .doOnCancel {
                logger.debug(
                    "emitFirstHeartbeat cancel for testId: {}, behandlingId: {}, traceId: {}",
                    testId,
                    behandlingId,
                    traceId,
                )
            }
            .doOnTerminate {
                logger.debug(
                    "emitFirstHeartbeat terminate for testId: {}, behandlingId: {}, traceId: {}",
                    testId,
                    behandlingId,
                    traceId,
                )
            }
        return emitFirstHeartbeat
    }

    private fun getHeartbeatStream(
        behandlingId: UUID,
        testId: String?,
        listenerStartTime: LocalDateTime,
        traceId: String,
    ): Flux<ServerSentEvent<JsonNode>> {
        val heartbeatStream: Flux<ServerSentEvent<JsonNode>> = Flux.interval(Duration.ofSeconds(10))
            .map {
                logger.debug(
                    "creating heartbeat event for testId: {}, behandlingId: {}, with start time: {}, traceId: {}",
                    testId,
                    behandlingId,
                    listenerStartTime,
                    traceId,
                )
                toHeartBeatServerSentEvent()
            }
            .doFinally { signalType ->
                logger.debug(
                    "heartbeat events closed for testId: {}, behandlingId: {}. SignalType: {}, with start time: {}, traceId: {}",
                    testId,
                    behandlingId,
                    signalType,
                    listenerStartTime,
                    traceId,
                )
                gaugeBehandlingHeartbeat.decrementAndGet()
            }
            .doOnCancel {
                logger.debug(
                    "heartbeat cancel for testId: {}, behandlingId: {}, with start time: {}, traceId: {}",
                    testId,
                    behandlingId,
                    listenerStartTime,
                    traceId,
                )
            }
            .doOnTerminate {
                logger.debug(
                    "heartbeat terminate for testId: {}, behandlingId: {}, with start time: {}, traceId: {}",
                    testId,
                    behandlingId,
                    listenerStartTime,
                    traceId,
                )
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