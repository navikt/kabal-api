package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.clients.klagelookup.Sak
import no.nav.klage.oppgave.domain.person.Person
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.stereotype.Service

@Service
class PersonService(
    private val klageLookupGateway: KlageLookupGateway,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    /**
     * Caller must perform appropriate access checks before calling this method
     */
    fun getPerson(fnr: String, sak: Sak?): Person {
        return klageLookupGateway.getPerson(fnr = fnr, sak = sak)
    }

    fun getFoedselsnummerFromIdent(ident: String): String {
        return klageLookupGateway.getFoedselsnummerFromIdent(ident = ident)
    }

    fun getAktoerIdFromIdent(ident: String): String {
        return klageLookupGateway.getAktoerIdFromIdent(ident = ident)
    }

    fun personExists(fnr: String): Boolean {
        try {
            getPerson(fnr = fnr, sak = null)
        } catch (e: Exception) {
            return false
        }
        return true
    }
}