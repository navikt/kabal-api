package no.nav.klage.oppgave.clients.pdl.kafka

import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.event.EventListener
import org.springframework.kafka.event.ListenerContainerIdleEvent
import org.springframework.stereotype.Component

@Component
class LeesahHealthIndication {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    var kafkaConsumerIdleAfterStartup = false

    @EventListener(condition = "event.listenerId.startsWith('kabalApiLeesahListener-')")
    fun eventHandler(event: ListenerContainerIdleEvent) {
        if (!kafkaConsumerIdleAfterStartup) {
            logger.debug("Mottok ListenerContainerIdleEvent fra kabalApiLeesahListener")
        }
        kafkaConsumerIdleAfterStartup = true
    }
}