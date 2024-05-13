package no.nav.klage.oppgave.clients.egenansatt

import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Service
class EgenAnsattService {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    private val egenAnsattMap: ConcurrentMap<String, EgenAnsatt> = ConcurrentHashMap()

    fun erEgenAnsatt(foedselsnr: String): Boolean =
        egenAnsattMap[foedselsnr]?.erGyldig() ?: false

    fun oppdaterEgenAnsatt(foedselsnr: String, egenAnsatt: EgenAnsatt) {
        logger.debug("Oppdaterer egenansatt. egenAnsattMap.size=${egenAnsattMap.size}")
        egenAnsattMap[foedselsnr] = egenAnsatt
    }
}