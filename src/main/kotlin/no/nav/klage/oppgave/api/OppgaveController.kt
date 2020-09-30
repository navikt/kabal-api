package no.nav.klage.oppgave.api

import no.nav.klage.oppgave.api.OppgaveController.Companion.ISSUER_AAD
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(issuer = ISSUER_AAD)
class OppgaveController(
    val tokenValidationContextHolder: TokenValidationContextHolder,
    val clientConfigurationProperties: ClientConfigurationProperties,
    val oAuth2AccessTokenService: OAuth2AccessTokenService
) {

    companion object {
        private val logger = getLogger(javaClass.enclosingClass)
        const val ISSUER_AAD = "aad"
    }

    /**
     * Show information about a token. Used for testing purposes.
     */
    @GetMapping("/tokeninfo")
    fun getInfo(): Map<String, Any> {
        val tokenValidationContext = tokenValidationContextHolder.tokenValidationContext
        return tokenValidationContext.getJwtToken(ISSUER_AAD)?.jwtTokenClaims?.allClaims ?: emptyMap()
    }

    @GetMapping("/graphtoken")
    fun getGraphToken(): String {
        val clientProperties = clientConfigurationProperties.registration["example-onbehalfof"]
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return response.accessToken
    }

}