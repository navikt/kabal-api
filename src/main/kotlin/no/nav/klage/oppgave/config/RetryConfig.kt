package no.nav.klage.oppgave.config

import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.retry.annotation.EnableRetry

@Configuration
@EnableRetry
class RetryConfiguration {
    @Bean
    fun loggingRetryListener(): RetryListener = LoggingRetryListener()
}

class LoggingRetryListener : RetryListener {
    private val logger = getLogger(javaClass)

    override fun <T : Any?, E : Throwable?> onError(
        context: RetryContext,
        callback: RetryCallback<T?, E?>,
        throwable: Throwable
    ) {
        val methodName = context.getAttribute("method")?.toString() ?: "unknown method"
        val retryCount = context.retryCount

        logger.debug("Retry attempt $retryCount for $methodName due to: ${throwable.javaClass.name}")

        super.onError(context, callback, throwable)
    }
}