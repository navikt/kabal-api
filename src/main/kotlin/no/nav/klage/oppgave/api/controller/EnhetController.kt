package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.clients.norg2.Enhet
import no.nav.klage.oppgave.clients.norg2.Norg2Client
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.service.EnhetService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/enheter")
class EnhetController(
    private val enhetService: EnhetService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val norg2Client: Norg2Client
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
            "{} is requested by ident {} for klagebehandlingId {}",
            ::getRelevantYtelser.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
        )

        return enhetService.getAllRelevantYtelserForEnhet(enhet)
    }

    @Hidden
    @GetMapping("/{enhetNr}")
    fun getEnhet(@PathVariable("enhetNr") enhetNr: String): Enhet = norg2Client.fetchEnhet(enhetNr)

    private fun verifyKabalInnsynEgenEnhet() {
        if (!innloggetSaksbehandlerService.hasKabalInnsynEgenEnhetRole()) {
            throw MissingTilgangException("Missing innsyn egen enhet access.")
        }
    }
}