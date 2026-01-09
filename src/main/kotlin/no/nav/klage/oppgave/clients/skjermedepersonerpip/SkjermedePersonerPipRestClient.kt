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

    fun isSkjermet(fnr: String, systemContext: Boolean): Boolean {
        logger.debug("Calling isSkjermet")
        val token = if (systemContext) {
            tokenUtil.getAppAccessTokenWithSkjermedePersonerPipScope()
        } else {
            tokenUtil.getSaksbehandlerAccessTokenWithSkjermedePersonerPipScope()
        }
        return runWithTimingAndLogging {
            skjermedePersonerPipWebClient.post()
                .uri { it.path("/skjermet").build() }
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer $token"
                )
                .bodyValue(IsSkjermetRequest(personident = fnr))
                .retrieve()
                .bodyToMono<Boolean>()
                .block()
                ?: throw RuntimeException("Could not get skjermet status.")
        }
    }

    fun isSkjermetBulk(fnrList: List<String>, systemContext: Boolean): Map<String, Boolean> {
        logger.debug("Calling isSkjermetBulk")
        val token = if (systemContext) {
            tokenUtil.getAppAccessTokenWithSkjermedePersonerPipScope()
        } else {
            tokenUtil.getSaksbehandlerAccessTokenWithSkjermedePersonerPipScope()
        }
        return runWithTimingAndLogging {
            skjermedePersonerPipWebClient.post()
                .uri { it.path("/skjermetBulk").build() }
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer $token"
                )
                .bodyValue(IsSkjermetBulkRequest(personidenter = fnrList))
                .retrieve()
                .bodyToMono<Map<String, Boolean>>()
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

    data class IsSkjermetRequest(
        val personident: String,
    )

    data class IsSkjermetBulkRequest(
        val personidenter: List<String>,
    )
}