package no.nav.klage.oppgave.util

import io.opentelemetry.api.trace.Span
import no.nav.klage.oppgave.domain.AuditLogEvent
import no.nav.klage.oppgave.domain.AuditLogEvent.Level.INFO
import no.nav.klage.oppgave.domain.AuditLogEvent.Level.WARN
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.lang.String.join

@Component
class AuditLogger(
    @Value("\${spring.application.name}") private val applicationName: String
) {

    companion object {
        val auditLogger = getAuditLogger()
    }

    fun log(logEvent: AuditLogEvent) {
        when (logEvent.logLevel) {
            WARN -> {
                auditLogger.warn(compileLogMessage(logEvent))
            }
            INFO -> {
                auditLogger.info(compileLogMessage(logEvent))
            }
        }
    }

    private fun compileLogMessage(logEvent: AuditLogEvent): String {
        //Field descriptions from CEF documentation (#tech-logg_analyse_og_datainnsikt):
        /*
        Set to: 0 (zero)
         */
        val version = "CEF:0"
        /*
        Arena, Bisys etc
         */
        val deviceVendor = "klage og anke"
        /*
        The name of the log that originated the event. Auditlog, leselogg, ABAC-Audit, Sporingslogg
         */
        val deviceProduct = applicationName
        /*
        The version of the logformat. 1.0
         */
        val deviceVersion = "1.0"
        /*
        The text representing the type of the event. For example audit:access, audit:edit
         */
        val deviceEventClassId = logEvent.action.value
        /*
        The description of the event. For example 'ABAC sporingslogg' or 'Database query'
         */
        val name = "$applicationName audit log"
        /*
        The severity of the event (INFO or WARN)
         */
        val severity = logEvent.logLevel.name

        val extensions = join(" ", getExtensions(logEvent))

        return join(
            "|", listOf(
                version,
                deviceVendor,
                deviceProduct,
                deviceVersion,
                deviceEventClassId,
                name,
                severity,
                extensions
            )
        )
    }

    private fun getExtensions(logEvent: AuditLogEvent): List<String> =
        listOf(
            "end=${System.currentTimeMillis()}",
            "suid=${logEvent.navIdent}",
            "duid=${logEvent.personFnr}",
            "sproc=${Span.current().spanContext.traceId}}",
            "msg=${logEvent.message}",
        )
}