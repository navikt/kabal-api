package no.nav.klage.oppgave.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Mono
import java.util.*

fun getLogger(forClass: Class<*>): Logger = LoggerFactory.getLogger(forClass)

fun getSecureLogger(): Logger = LoggerFactory.getLogger("secure")

fun getAuditLogger(): Logger = LoggerFactory.getLogger("audit")

fun logKlagebehandlingMethodDetails(methodName: String, innloggetIdent: String, klagebehandlingId: UUID, logger: Logger) {
    logger.debug(
        "{} is requested by ident {} for klagebehandlingId {}",
        methodName,
        innloggetIdent,
        klagebehandlingId
    )
}

fun logBehandlingMethodDetails(methodName: String, innloggetIdent: String, behandlingId: UUID, logger: Logger) {
    logger.debug(
        "{} is requested by ident {} for behandlingId {}",
        methodName,
        innloggetIdent,
        behandlingId
    )
}

fun logMethodDetails(methodName: String, innloggetIdent: String, logger: Logger) {
    logger.debug(
        "{} is requested by ident {}",
        methodName,
        innloggetIdent,
    )
}

fun logErrorResponse(response: ClientResponse, functionName: String, logger: Logger): Mono<RuntimeException> {
    return response.bodyToMono(String::class.java).map {
        val errorString =
            "Got ${response.statusCode()} when requesting $functionName - response body: '$it'"
        logger.error(errorString)
        RuntimeException(errorString)
    }
}