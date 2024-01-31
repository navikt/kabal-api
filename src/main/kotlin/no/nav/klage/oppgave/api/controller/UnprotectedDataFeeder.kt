package no.nav.klage.oppgave.api.controller

import jakarta.validation.Valid
import no.nav.klage.oppgave.api.view.OversendtKlageAnkeV3
import no.nav.klage.oppgave.service.MottakService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Profile("dev-gcp", "local")
@RestController
class UnprotectedDataFeeder(
    private val mottakService: MottakService,
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
}
