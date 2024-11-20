package no.nav.klage.innsyn.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.innsyn.api.view.InnsynResponse
import no.nav.klage.innsyn.service.InnsynService
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(
    name = "kabal-innsyn",
    description = "api for innsyn i brukeres saker"
)
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("api/innsyn")
class InnsynController(
    private val innsynService: InnsynService,
    private val tokenUtil: TokenUtil,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    //Unprotected while developing
    @Unprotected
    @Operation(
        summary = "Hent en brukers saker",
        description = "Hent en brukers saker, basert på brukerens ident hentet fra token"
    )
    @GetMapping("/saker")
    fun saker(): InnsynResponse {
//        logMethodDetails(
//            ::saker.name,
//            tokenUtil.getIdent(), //TODO
//            secureLogger,
//        )

        return innsynService.getSakerForBruker(fnr = "14828897927") //TODO get from token
    }
}