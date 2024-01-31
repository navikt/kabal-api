package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.service.EnhetService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/enheter")
class EnhetController(
    private val enhetService: EnhetService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/{enhet}/ytelser")
    fun getRelevantYtelser(
        @PathVariable("enhet") enhet: String
    ): List<String> {
        verifyKabalInnsynEgenEnhet()
        logger.debug(
            "{} is requested by ident {}",
            ::getRelevantYtelser.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
        )

        return enhetService.getAllRelevantYtelserForEnhet(enhet)
    }

    private fun verifyKabalInnsynEgenEnhet() {
        if (!innloggetSaksbehandlerService.hasKabalInnsynEgenEnhetRole()) {
            throw MissingTilgangException("Missing innsyn egen enhet access.")
        }
    }
}