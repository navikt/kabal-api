package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.api.view.OversendtAnkeITrygderettenV1
import no.nav.klage.oppgave.api.view.OversendtKlageAnkeV3
import no.nav.klage.oppgave.service.AnkeITrygderettenbehandlingService
import no.nav.klage.oppgave.service.MottakService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid

@RestController
class UnprotectedDataFeeder(
    private val mottakService: MottakService,
    private val ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    @Unprotected
    @PostMapping("/internal/manualfeed")
    fun sendInnKlageV3(
        @Valid @RequestBody oversendtKlage: OversendtKlageAnkeV3
    ) {
        logger.warn("Data manually fed to Kabal through unprotected endpoint")
        secureLogger.warn("Data $oversendtKlage fed to Kabal through unprotected endpoint")
        mottakService.createMottakForKlageAnkeV3(oversendtKlage)
    }

    @Unprotected
    @PostMapping("/internal/manualankeitrygderettenfeed")
    fun sendInnAnkeITrygderettenV1(
        @Valid @RequestBody oversendtAnkeITrygderetten: OversendtAnkeITrygderettenV1
    ) {
        logger.warn("Ankeitrygderetten data manually fed to Kabal through unprotected endpoint")
        secureLogger.warn("Ankeitrygderetten data $oversendtAnkeITrygderetten fed to Kabal through unprotected endpoint")
        ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandling(oversendtAnkeITrygderetten)
    }

}
