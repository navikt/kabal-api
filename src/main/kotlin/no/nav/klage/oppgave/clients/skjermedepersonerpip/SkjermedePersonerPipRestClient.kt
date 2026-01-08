package no.nav.klage.oppgave.clients.skjermedepersonerpip

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono


@Component
class SkjermedePersonerPipRestClient(
    private val skjermedePersonerPipWebClient: WebClient,
    private val tokenUtil: TokenUtil,
)  {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun personIsSkjermet(fnr: String): Boolean {
        logger.debug("Calling personIsSkjermet")
        return runWithTimingAndLogging {
            skjermedePersonerPipWebClient.post()
                .uri { it.path("/skjermet").build() }
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer ${tokenUtil.getSaksbehandlerAccessTokenWithSkjermedePersonerPipScope()}"
                )
                .bodyValue(Request(personident = fnr))
                .retrieve()
                .bodyToMono<Boolean>()
                .block()
                ?: throw RuntimeException("Could not get skjermet status.")
        }
    }

    fun <T> runWithTimingAndLogging(block: () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return block.invoke()
        } finally {
            val end = System.currentTimeMillis()
            logger.debug("Time it took to call skjermedePersonerPip: ${end - start} millis")
        }
    }

    data class Request(
        val personident: String,
    )
}