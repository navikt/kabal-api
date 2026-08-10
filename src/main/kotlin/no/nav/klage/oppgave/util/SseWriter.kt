package no.nav.klage.oppgave.util

import jakarta.servlet.http.HttpServletResponse
import no.nav.klage.oppgave.util.SseWriter.Companion.HEARTBEAT_INTERVAL
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.io.PrintWriter
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Writes server-sent events to a [HttpServletResponse].
 *
 * The response is only touched on the first [send]: as long as nothing has been sent, [hasStarted] is
 * false and the caller can still let the normal error handling produce a regular (problem detail)
 * response body.
 *
 * Once the first event has been sent, a comment is written every [HEARTBEAT_INTERVAL] to keep the
 * connection alive and to notice a client that has gone away, even while the caller is busy doing
 * something that takes minutes. Remember to [close] the writer when done.
 */
class SseWriter(private val response: HttpServletResponse) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        private val HEARTBEAT_INTERVAL = Duration.ofSeconds(10)

        private val threadCount = AtomicInteger()

        //Shared by all streams. Each heartbeat is a single small write, so one thread is plenty.
        private val heartbeatExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "sse-heartbeat-${threadCount.incrementAndGet()}").apply { isDaemon = true }
        }
    }

    private val lock = Any()

    private var writer: PrintWriter? = null
    private var heartbeat: ScheduledFuture<*>? = null
    private var closed = false

    val hasStarted: Boolean
        get() = synchronized(lock) { writer != null }

    fun send(event: String, data: String) {
        synchronized(lock) {
            if (closed) return

            val out = writer ?: start()
            out.write("event: $event\ndata: $data\n\n")
            out.flush()

            //PrintWriter keeps its problems to itself, so we have to ask.
            if (out.checkError()) {
                logger.debug("Could not send event to client, closing stream for the rest of this request.")
                stopWriting()
            }
        }
    }

    fun close() {
        synchronized(lock) {
            stopWriting()
        }
    }

    private fun stopWriting() {
        closed = true
        heartbeat?.cancel(false)
        heartbeat = null
    }

    private fun start(): PrintWriter {
        response.contentType = MediaType.TEXT_EVENT_STREAM_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().headerValue)
        //Tell proxies not to buffer, or the client sees nothing until everything is done.
        response.setHeader("X-Accel-Buffering", "no")

        heartbeat = heartbeatExecutor.scheduleWithFixedDelay(
            ::sendHeartbeat,
            HEARTBEAT_INTERVAL.toMillis(),
            HEARTBEAT_INTERVAL.toMillis(),
            TimeUnit.MILLISECONDS,
        )

        return response.writer.also { writer = it }
    }

    private fun sendHeartbeat() {
        synchronized(lock) {
            if (closed) return

            val out = writer ?: return

            //A comment: ignored by the client, but it keeps the connection in use.
            out.write(": heartbeat\n\n")
            out.flush()

            if (out.checkError()) {
                logger.debug("Could not send heartbeat, closing stream for the rest of this request.")
                stopWriting()
            }
        }
    }
}
