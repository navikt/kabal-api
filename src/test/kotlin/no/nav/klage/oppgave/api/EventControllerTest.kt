package no.nav.klage.oppgave.api

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import no.nav.klage.oppgave.api.controller.EventController
import no.nav.klage.oppgave.config.MetricsConfiguration
import no.nav.klage.oppgave.config.SseShutdownSignal
import no.nav.klage.oppgave.domain.kafka.InternalBehandlingEvent
import no.nav.klage.oppgave.domain.kafka.InternalEventType
import no.nav.klage.oppgave.domain.kafka.InternalIdentityEvent
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.InternalKafkaEventStreams
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

class EventControllerTest {
    private val behandlingId = UUID.randomUUID()
    private val sakenGjelderIdentifikator = "12345678910"
    private val internalKafkaEventStreams = mockk<InternalKafkaEventStreams>()
    private val behandlingService = mockk<BehandlingService>()
    private val meterRegistry = SimpleMeterRegistry()
    private val sseShutdownSignal = SseShutdownSignal()
    private val controller =
        EventController(
            internalKafkaEventStreams = internalKafkaEventStreams,
            meterRegistry = meterRegistry,
            behandlingService = behandlingService,
            sseShutdownSignal = sseShutdownSignal,
        )

    @BeforeEach
    fun setup() {
        every { behandlingService.getBehandlingDetaljerView(behandlingId) } returns
            mockk { every { sakenGjelder.identifikator } returns sakenGjelderIdentifikator }
        // Like production, the Kafka streams never complete on their own.
        every { internalKafkaEventStreams.behandlingEvents() } returns Flux.never()
        every { internalKafkaEventStreams.identityEvents() } returns Flux.never()
    }

    @Test
    fun `completes open stream when the application shuts down`() {
        val events =
            assertTimeoutPreemptively(Duration.ofSeconds(5)) {
                controller
                    .events(behandlingId)
                    .doOnSubscribe {
                        Schedulers.parallel().schedule({ sseShutdownSignal.onShutdown() }, 200, TimeUnit.MILLISECONDS)
                    }.collectList()
                    .block()!!
            }

        assertThat(events.map { it.event() }).containsExactly("HEARTBEAT")
        assertAllEventListenersReleased()
    }

    @Test
    fun `completes immediately when connecting after shutdown has started`() {
        sseShutdownSignal.onShutdown()

        val events =
            assertTimeoutPreemptively(Duration.ofSeconds(5)) {
                controller.events(behandlingId).collectList().block()!!
            }

        assertThat(events).isEmpty()
        assertAllEventListenersReleased()
    }

    @Test
    fun `only delivers behandling events for the requested behandling`() {
        every { internalKafkaEventStreams.behandlingEvents() } returns
            emittingStream(
                consumerRecord(
                    offset = 0,
                    value = behandlingEventJson(behandlingId = UUID.randomUUID(), type = InternalEventType.MESSAGE),
                ),
                consumerRecord(offset = 1, value = "not json"),
                consumerRecord(offset = 2, value = behandlingEventJson(behandlingId = behandlingId, type = InternalEventType.UTFALL)),
            )

        val events = collectUntilShutdown()

        assertThat(events.map { it.event() }).containsExactlyInAnyOrder("HEARTBEAT", InternalEventType.UTFALL.name)
        assertThat(events.single { it.event() == InternalEventType.UTFALL.name }.id()).isEqualTo("2")
        assertAllEventListenersReleased()
    }

    @Test
    fun `only delivers identity events for sakenGjelder`() {
        every { internalKafkaEventStreams.identityEvents() } returns
            emittingStream(
                consumerRecord(offset = 0, value = identityEventJson(identifikator = "10987654321", type = InternalEventType.MESSAGE)),
                consumerRecord(
                    offset = 1,
                    value = identityEventJson(identifikator = sakenGjelderIdentifikator, type = InternalEventType.KLAGER),
                ),
            )

        val events = collectUntilShutdown()

        assertThat(events.map { it.event() }).containsExactlyInAnyOrder("HEARTBEAT", InternalEventType.KLAGER.name)
        assertThat(events.single { it.event() == InternalEventType.KLAGER.name }.id()).isEqualTo("1")
        assertAllEventListenersReleased()
    }

    private fun collectUntilShutdown() =
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            controller
                .events(behandlingId)
                .doOnSubscribe {
                    Schedulers.parallel().schedule({ sseShutdownSignal.onShutdown() }, 500, TimeUnit.MILLISECONDS)
                }.collectList()
                .block()!!
        }

    private fun behandlingEventJson(
        behandlingId: UUID,
        type: InternalEventType,
    ): String =
        jacksonObjectMapper().writeValueAsString(InternalBehandlingEvent(behandlingId = behandlingId.toString(), type = type, data = "{}"))

    private fun identityEventJson(
        identifikator: String,
        type: InternalEventType,
    ): String = jacksonObjectMapper().writeValueAsString(InternalIdentityEvent(identifikator = identifikator, type = type, data = "{}"))

    private fun consumerRecord(
        offset: Long,
        value: String,
    ): ConsumerRecord<String, String> = ConsumerRecord("topic", 0, offset, "key", value)

    private fun emittingStream(vararg records: ConsumerRecord<String, String>): Flux<ConsumerRecord<String, String>> =
        Flux.fromIterable(records.toList()).concatWith(Flux.never())

    private fun assertAllEventListenersReleased() {
        listOf("behandling", "identity", "behandling-heartbeat").forEach { eventType ->
            val gauge =
                meterRegistry
                    .get(MetricsConfiguration.CURRENT_EVENT_LISTENERS)
                    .tag("event-type", eventType)
                    .gauge()
            assertThat(gauge.value()).describedAs("event-type $eventType").isZero()
        }
    }
}
