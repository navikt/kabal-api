package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.service.PersonProtectionService
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/api/person-protection")
class PersonProtectionController(
    private val personProtectionService: PersonProtectionService,
    private val tokenUtil: TokenUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/changed")
    @ResponseStatus(HttpStatus.OK)
    fun personProtectionChanged(
        @RequestBody input: PersonProtectionChangedInput,
    ) {
        authorizeCaller()
        personProtectionService.handlePersonProtectionChanged(foedselsnummer = input.foedselsnummer)
    }

    private fun authorizeCaller() {
        if (tokenUtil.getCurrentTokenType() != TokenUtil.TokenType.CC) {
            throw MissingTilgangException("Endpoint requires a client-credentials token")
        }
        val caller = tokenUtil.getCallingApplication()
        if (caller != "klage-lookup") {
            logger.warn("Rejected person-protection/changed call from unauthorized client '{}'", caller)
            throw MissingTilgangException("Client '$caller' is not authorized to call this endpoint")
        }
    }

    data class PersonProtectionChangedInput(
        val foedselsnummer: String,
    )
}