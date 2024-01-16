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
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/behandlinger/{behandlingId}")
class EventController(
    private val aivenKafkaClientCreator: AivenKafkaClientCreator,
    private val meterRegistry: MeterRegistry,
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
        logger.debug(
            "new events called with testId: {}, for behandlingId: {} and protocol: {}",
            testId,
            behandlingId,
            request.protocol
        )

        val behandling = behandlingService.getBehandlingAndCheckLeseTilgangForPerson(behandlingId = behandlingId)

        //https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#mvc-ann-async-disconnects
        val heartbeatStream: Flux<ServerSentEvent<JsonNode>> = getHeartbeatStream(
            behandlingId = behandlingId,
            testId = testId
        )

        val behandlingEventPublisher = getBehandlingEventPublisher(
            behandlingId = behandlingId,
            testId = testId,
        )

        val identityEventPublisher = getIdentityEventPublisher(
            behandling = behandling,
            testId = testId,
        )

        val emitFirstHeartbeat = getFirstHeartbeat(
            behandlingId = behandlingId,
            testId = testId,
        )

        return behandlingEventPublisher
            .mergeWith(emitFirstHeartbeat)
            .mergeWith(heartbeatStream)
            .mergeWith(identityEventPublisher)
    }

    private fun getBehandlingEventPublisher(
        behandlingId: UUID,
        testId: String?
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
                logger.debug("events cancel for testId: {}, behandlingId: {}", testId, behandlingId)
            }
            .doOnTerminate {
                logger.debug("events terminate for testId: {}, behandlingId: {}", testId, behandlingId)
            }
            .doFinally { signalType ->
                logger.debug(
                    "events closed for testId: {}, behandlingId: {}. SignalType: {}",
                    testId,
                    behandlingId,
                    signalType,
                )
                gaugeBehandling.decrementAndGet()
            }

        gaugeBehandling.incrementAndGet()

        return flux
    }

    private fun getIdentityEventPublisher(
        behandling: Behandling,
        testId: String?
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
                logger.debug("events cancel for testId: {}, behandlingId: {}", testId, behandling.id)
            }
            .doOnTerminate {
                logger.debug("events terminate for testId: {}, behandlingId: {}", testId, behandling.id)
            }
            .doFinally { signalType ->
                logger.debug(
                    "events closed for testId: {}, behandlingId: {}. SignalType: {}",
                    testId,
                    behandling.id,
                    signalType,
                )
                gaugeIdentity.decrementAndGet()
            }

        gaugeIdentity.incrementAndGet()

        return flux
    }

    private fun getFirstHeartbeat(
        behandlingId: UUID,
        testId: String?,
    ): Flux<ServerSentEvent<JsonNode>> {
        val emitFirstHeartbeat = Flux.generate<ServerSentEvent<JsonNode>> {
            it.next(toHeartBeatServerSentEvent())
            it.complete()
        }
            .doFinally { signalType ->
                logger.debug(
                    "emitFirstHeartbeat closed for testId: {}, behandlingId: {}. SignalType: {}",
                    testId,
                    behandlingId,
                    signalType
                )
            }
            .doOnCancel {
                logger.debug("emitFirstHeartbeat cancel for testId: {}, behandlingId: {}", testId, behandlingId)
            }
            .doOnTerminate {
                logger.debug("emitFirstHeartbeat terminate for testId: {}, behandlingId: {}", testId, behandlingId)
            }
        return emitFirstHeartbeat
    }

    private fun getHeartbeatStream(
        behandlingId: UUID,
        testId: String?,
    ): Flux<ServerSentEvent<JsonNode>> {
        val heartbeatStream: Flux<ServerSentEvent<JsonNode>> = Flux.interval(Duration.ofSeconds(10))
            .map {
                logger.debug("creating heartbeat event for testId: {}, behandlingId: {}", testId, behandlingId)
                toHeartBeatServerSentEvent()
            }
            .doFinally { signalType ->
                logger.debug(
                    "heartbeat closed for testId: {}, behandlingId: {}. SignalType: {}",
                    testId,
                    behandlingId,
                    signalType,
                )
                gaugeBehandlingHeartbeat.decrementAndGet()
            }
            .doOnCancel {
                logger.debug("heartbeat cancel for testId: {}, behandlingId: {}", testId, behandlingId)
            }
            .doOnTerminate {
                logger.debug("heartbeat terminate for testId: {}, behandlingId: {}", testId, behandlingId)
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