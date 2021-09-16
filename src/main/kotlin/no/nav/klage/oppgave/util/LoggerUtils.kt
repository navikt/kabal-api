package no.nav.klage.oppgave.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
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