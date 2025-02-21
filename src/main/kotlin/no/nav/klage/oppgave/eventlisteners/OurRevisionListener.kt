package no.nav.klage.oppgave.eventlisteners

import io.opentelemetry.api.trace.Span
import no.nav.klage.oppgave.domain.OurRevision
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.hibernate.envers.RevisionListener
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Service
class OurRevisionListener(
    private val tokenUtil: TokenUtil,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) : RevisionListener {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securelogger = getSecureLogger()
    }

    override fun newRevision(revisionEntity: Any?) {
        revisionEntity as OurRevision

        var actor: String? = null

        val request = try {
            val requestAttributes = RequestContextHolder.getRequestAttributes()
            if (requestAttributes != null) {
                val request = (requestAttributes as ServletRequestAttributes).request
                request.method + " " + request.requestURI
            } else {
                //no exception occurred, we just don't have a request
                actor = systembrukerIdent
                null
            }
        } catch (e: Exception) {
            logger.debug("No request found to set on revision entity. Setting to null.", e)
            null
        }

        val navIdentFromToken = try {
            tokenUtil.getIdent()
        } catch (e: Exception) {
            logger.debug("No NAVIdent found in token.", e)
            null
        }

        val callingApplication = try {
            tokenUtil.getCallingApplication()
        } catch (e: Exception) {
            logger.debug("Failed to get calling application from token.", e)
            null
        }

        if (navIdentFromToken != null || callingApplication != null) {
            actor = navIdentFromToken ?: callingApplication
        }

        val traceId = try {
            Span.current().spanContext.traceId
        } catch (e: Exception) {
            logger.warn("Failed to set traceId on revision entity. Setting 'unknown'.", e)
            "unknown"
        }

        revisionEntity.request = request
        revisionEntity.actor = actor ?: "unknown"
        revisionEntity.traceId = traceId
    }
}