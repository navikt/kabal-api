package no.nav.klage.oppgave.config

import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.context.annotation.Configuration

@EnableJwtTokenValidation(ignore = ["org.springdoc", "org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController"])
@EnableOAuth2Client(cacheEnabled = true)
@Configuration
internal class SecurityConfiguration {

    companion object {
        const val ISSUER_AAD = "aad"
        const val TOKEN_X = "tokenx"
    }
}
