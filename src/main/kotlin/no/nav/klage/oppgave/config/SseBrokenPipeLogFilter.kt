package no.nav.klage.oppgave.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.turbo.TurboFilter
import ch.qos.logback.core.spi.FilterReply
import no.nav.klage.oppgave.util.getLogger
import org.slf4j.Marker

class SseBrokenPipeLogFilter : TurboFilter() {
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
        throwable: Throwable?,
    ): FilterReply {
        if (throwable != null) {
            if (
                (
                    throwable.javaClass.name == "java.io.IOException" &&
                        throwable.message == "Broken pipe" &&
                        logger?.name?.contains("org.apache.catalina.core.ContainerBase") == true
                    )
            /*
             ||
            (throwable.javaClass.name == "AsyncRequestNotUsableException" &&
             throwable.message?.contains("Broken pipe", ignoreCase = true) == true
            )
             */
            ) {
                ourLogger.debug(
                    "Suppressing error log message when broken pipe and logger is ${logger.name}. This is probably due to lost client during async/SSE operations.",
                )
                return FilterReply.DENY
            }
        }

        if (level == Level.WARN && logger?.name == "org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver" &&
            format?.contains("java.io.IOException: Broken pipe") == true
        ) {
            ourLogger.debug(
                "Suppressing warning log message when broken pipe and logger is ${logger.name}. This is probably due to lost client during async/SSE operations.",
            )
            return FilterReply.DENY
        }

        if (level == Level.WARN && logger?.name == "no.nav.klage.config.problem.GlobalExceptionHandler" &&
            format?.contains("Response already committed") == true
        ) {
            ourLogger.debug(
                "Suppressing warning log message when response already committed and logger is ${logger.name}. This is probably due to lost client during async/SSE operations.",
            )
            return FilterReply.DENY
        }

        if (level == Level.WARN &&
            logger?.name == "org.eclipse.jetty.ee11.servlet.ServletChannel" &&
            throwable != null &&
            throwable.javaClass.name == "jakarta.servlet.ServletException" &&
            throwable.message?.contains("Request processing failed") == true &&
            format?.contains("events") == true
        ) {
            val causeChain = causeChain(throwable = throwable)
            if (causeChain.any { it.isClientDisconnect() }) {
                ourLogger.debug("Suppressing warning log message. This is probably due to lost client during async/SSE operations.")
            } else {
                // Exception messages are not logged: they may contain personal data from event payloads.
                val rootCause = causeChain.last()
                ourLogger.warn(
                    "SSE request failed on the server side. uri={}, causes={}, rootCauseAt={}",
                    format.take(200),
                    causeChain.joinToString(separator = " <- ") { it.javaClass.name },
                    rootCause.stackTrace.take(3).joinToString(separator = " | "),
                )
            }
            return FilterReply.DENY
        }

        return FilterReply.NEUTRAL
    }

    private fun causeChain(throwable: Throwable): List<Throwable> =
        generateSequence(throwable) { current -> current.cause?.takeIf { it !== current } }
            .take(10)
            .toList()

    private fun Throwable.isClientDisconnect(): Boolean =
        javaClass.name in CLIENT_DISCONNECT_EXCEPTIONS ||
            (
                this is java.io.IOException &&
                    message?.let { it.contains("Broken pipe") || it.contains("Connection reset") } == true
                )
}

private val CLIENT_DISCONNECT_EXCEPTIONS =
    setOf(
        "org.springframework.web.context.request.async.AsyncRequestNotUsableException",
        "org.eclipse.jetty.io.EofException",
        "java.nio.channels.ClosedChannelException",
    )
