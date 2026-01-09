package no.nav.klage.oppgave.clients.egenansatt

import no.nav.klage.oppgave.clients.skjermedepersonerpip.SkjermedePersonerPipRestClient
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Service
class EgenAnsattService(
    private val skjermedePersonerPipRestClient: SkjermedePersonerPipRestClient
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    private val egenAnsattMap: ConcurrentMap<String, EgenAnsatt> = ConcurrentHashMap()

    fun erEgenAnsatt(foedselsnr: String): Boolean {
        return egenAnsattMap[foedselsnr]?.erGyldig() ?: skjermedePersonerPipRestClient.isSkjermet(
            fnr = foedselsnr,
            systemContext = true,
        )
    }

    fun oppdaterEgenAnsatt(foedselsnr: String, egenAnsatt: EgenAnsatt) {
        logger.debug("Oppdaterer egenansatt. egenAnsattMap.size=${egenAnsattMap.size}")
        egenAnsattMap[foedselsnr] = egenAnsatt
    }
}