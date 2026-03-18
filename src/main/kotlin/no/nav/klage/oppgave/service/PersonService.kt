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

    fun fillPersonCache(fnrList: List<String>) {
        TODO()
    }

    fun getFoedselsnummerFromIdent(ident: String): String {
        return klageLookupGateway.getFoedselsnummerFromIdent(ident = ident)
    }

    fun getAktoerIdFromIdent(ident: String): String {
        return klageLookupGateway.getAktoerIdFromIdent(ident = ident)
    }

    fun personExists(fnr: String): Boolean {
        try {
            getPerson(fnr = fnr, null)
        } catch (e: Exception) {
            return false
        }
        return true
    }

    fun fillCacheWithAllMissingPersons() {
        TODO()
//        val start = System.currentTimeMillis()
//        logger.debug("Finding all persons in open behandlinger to fill cache in pod ${InetAddress.getLocalHost().hostName}")
//        val allOpenBehandlinger = behandlingRepository.findByFerdigstillingIsNullAndFeilregistreringIsNull()
//        logger.debug("Found all open behandlinger: ${allOpenBehandlinger.size}, took ${System.currentTimeMillis() - start} ms in pod ${InetAddress.getLocalHost().hostName}")
//
//        val allSakenGjelderFnr = allOpenBehandlinger.filter { it.sakenGjelder.partId.type == PartIdType.PERSON }
//            .map { it.sakenGjelder.partId.value }
//            .distinct()
//
//        val allKlagerFnr = allOpenBehandlinger.filter { it.klager.partId.type == PartIdType.PERSON }
//            .map { it.klager.partId.value }
//            .distinct()
//
//        val allFullmektigFnr = allOpenBehandlinger.filter { it.prosessfullmektig?.partId?.type == PartIdType.PERSON }
//            .map { it.prosessfullmektig?.partId?.value }
//            .distinct()
//
//        val allPersonsInOpenBehandlingerFnr = (allSakenGjelderFnr + allKlagerFnr + allFullmektigFnr).filterNotNull().distinct()
//
//        logger.debug("Found all distinct persons: ${allPersonsInOpenBehandlingerFnr.size}, took ${System.currentTimeMillis() - start} ms in pod ${InetAddress.getLocalHost().hostName}")
//
//        fillPersonCache(allPersonsInOpenBehandlingerFnr)
//
//        logger.debug("Finished inserting all persons from open behandlinger in cache in ${System.currentTimeMillis() - start} ms in pod ${InetAddress.getLocalHost().hostName}")
    }
}