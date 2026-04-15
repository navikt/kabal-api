package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.service.PersonProtectionService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/api/person-protection")
class PersonProtectionController(
    private val personProtectionService: PersonProtectionService,
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
        personProtectionService.handlePersonProtectionChanged(foedselsnummer = input.foedselsnummer)
    }

    data class PersonProtectionChangedInput(
        val foedselsnummer: String,
    )
}