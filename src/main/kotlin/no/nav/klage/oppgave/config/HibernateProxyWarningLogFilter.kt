package no.nav.klage.oppgave.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply
import no.nav.klage.oppgave.util.getLogger
import org.slf4j.Marker

class HibernateProxyWarningLogFilter : TurboFilter() {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val ourLogger = getLogger(javaClass.enclosingClass)
    }

    override fun decide(
        marker: Marker?,
        logger: Logger?,
        level: Level?,
        format: String?,
        params: Array<out Any>?,
        throwable: Throwable?
    ): FilterReply {
        if (level == Level.WARN &&
            logger?.name == "org.hibernate.metamodel.internal.EntityRepresentationStrategyPojoStandard" &&
            format?.contains("HHH000305: Could not create proxy factory for") == true
        ) {
            ourLogger.debug("Suppressing Hibernate proxy factory warning. This is expected for Kotlin entities with final getters.")
            return FilterReply.DENY
        }

        return FilterReply.NEUTRAL
    }
}