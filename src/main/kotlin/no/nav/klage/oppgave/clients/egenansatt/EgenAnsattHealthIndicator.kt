package no.nav.klage.oppgave.clients.egenansatt

import no.nav.klage.oppgave.util.getLogger
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.context.event.EventListener
import org.springframework.kafka.event.ListenerContainerIdleEvent
import org.springframework.stereotype.Component

@Component
class EgenAnsattHealthIndicator : HealthIndicator {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    private var kafkaConsumerHasReadAllMsgs = false


    @EventListener(condition = "event.listenerId.startsWith('klageEgenAnsattListener-')")
    fun eventHandler(event: ListenerContainerIdleEvent) {
        if (!kafkaConsumerHasReadAllMsgs) {
            logger.debug("Mottok ListenerContainerIdleEvent fra klageEgenAnsattListener")
        }
        kafkaConsumerHasReadAllMsgs = true
    }

    override fun health(): Health =
        if (kafkaConsumerHasReadAllMsgs) {
            Health.up().build()
        } else {
            Health.down().build()
        }
}
