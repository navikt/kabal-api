package no.nav.klage.oppgave.eventlisteners

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

        try {
            val requestAttributes = RequestContextHolder.getRequestAttributes()
            if (requestAttributes != null) {
                val request = (requestAttributes as ServletRequestAttributes).request
                revisionEntity.actor = tokenUtil.getIdent()
                revisionEntity.request = request.method + " " + request.requestURI
            } else {
                revisionEntity.actor = systembrukerIdent
            }
        } catch (e: Exception) {
            logger.warn("Failed to set correct actor and/or request on revision entity. Setting 'unknown'.", e)
            revisionEntity.actor = "unknown"
        }
    }
}