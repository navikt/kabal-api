package no.nav.klage.oppgave.config

import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.annotation.Configuration
import java.util.concurrent.atomic.AtomicInteger


@Configuration
class MetricsConfiguration {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        const val MOTTATT_KLAGEANKE = "funksjonell.mottattklageanke"
        const val CURRENT_EVENT_LISTENERS = "technical.current_event_listeners"
    }
}

fun MeterRegistry.incrementMottattKlageAnke(kildesystem: String, ytelse: String, type: String) {
    this.counter(
        MetricsConfiguration.MOTTATT_KLAGEANKE,
        "kildesystem",
        kildesystem,
        "ytelse",
        ytelse,
        "type",
        type
    ).increment()
}

fun MeterRegistry.getGauge(eventType: String, currentCount: AtomicInteger): AtomicInteger {
    return this.gauge(
        /* name = */ MetricsConfiguration.CURRENT_EVENT_LISTENERS,
        /* tags = */ listOf(Tag.of("event-type", eventType)),
        /* number = */ currentCount
    )!!
}

fun MeterRegistry.getHistogram(name: String, baseUnit: String): DistributionSummary {
    return DistributionSummary
        .builder(name)
        .baseUnit(baseUnit)
        .register(this)
}