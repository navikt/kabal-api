package no.nav.klage.oppgave.config

import no.nav.klage.oppgave.util.getLogger
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.ReadinessState
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Gate that tracks application health and allows scheduled jobs to check
 * if the application is ready before executing.
 * Also waits a warmup period after readiness to ensure connections are established.
 */
@Component
class SchedulerHealthGate {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val WARMUP_DURATION = Duration.ofMinutes(1)
    }

    @Volatile
    private var readyAt: Instant? = null

    @EventListener
    fun onReadinessStateChange(event: AvailabilityChangeEvent<ReadinessState>) {
        val newState = event.state
        if (newState == ReadinessState.ACCEPTING_TRAFFIC) {
            if (readyAt == null) {
                readyAt = Instant.now()
                logger.debug("Application is now ready. Scheduled jobs will be allowed to run after ${WARMUP_DURATION.seconds}s warmup period.")
            }
        } else {
            logger.debug("Application is no longer ready. Scheduled jobs will be skipped.")
            readyAt = null
        }
    }

    fun isReady(): Boolean {
        val ready = readyAt ?: return false
        return Instant.now().isAfter(ready.plus(WARMUP_DURATION))
    }
}