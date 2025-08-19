package no.nav.klage.dokument.config

import no.nav.klage.dokument.service.DuaAccessPolicy
import no.nav.klage.oppgave.util.getLogger
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class DuaAccessPolicyInitializer : ApplicationRunner {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    override fun run(args: ApplicationArguments) {
        DuaAccessPolicy.initializeAccessMapFromCsv("/dua/access_map.csv")
        logger.info("Dua access map initialized from resource '/dua/access_map.csv'")
    }
}