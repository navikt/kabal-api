package no.nav.klage.oppgave.api

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import no.nav.klage.oppgave.api.controller.EventController
import no.nav.klage.oppgave.config.MetricsConfiguration
import no.nav.klage.oppgave.config.SseShutdownSignal
import no.nav.klage.oppgave.service.AivenKafkaClientCreator
import no.nav.klage.oppgave.service.BehandlingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

class EventControllerTest {
    private val behandlingId = UUID.randomUUID()
    private val aivenKafkaClientCreator = mockk<AivenKafkaClientCreator>()
    private val behandlingService = mockk<BehandlingService>()
    private val meterRegistry = SimpleMeterRegistry()
    private val sseShutdownSignal = SseShutdownSignal()
    private val controller =
        EventController(
            aivenKafkaClientCreator = aivenKafkaClientCreator,
            meterRegistry = meterRegistry,
            behandlingService = behandlingService,
            sseShutdownSignal = sseShutdownSignal,
        )

    @BeforeEach
    fun setup() {
        every { behandlingService.getBehandlingDetaljerView(behandlingId) } returns
            mockk { every { sakenGjelder.identifikator } returns "12345678910" }
        // Like production, the Kafka streams never complete on their own.
        every { aivenKafkaClientCreator.getNewKafkaInternalBehandlingEventReceiver().receive() } returns Flux.never()
        every { aivenKafkaClientCreator.getNewKafkaInternalIdentityEventReceiver().receive() } returns Flux.never()
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
