package no.nav.klage.oppgave.config

import no.nav.klage.auth.AzureAuthProperties
import no.nav.klage.auth.TokenXAuthProperties
import no.nav.klage.auth.azureJwtDecoder
import no.nav.klage.auth.tokenXJwtDecoder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Two filter chains, one per token type, so a token is only accepted where its issuer is expected:
 * TokenX (citizens) for /api/innsyn, Azure for everything else.
 * The decoders are created inside the chains, not as JwtDecoder beans, so Spring Boot cannot pick
 * the wrong one.
 */
@Configuration
@EnableWebSecurity
class SecurityConfiguration {
    companion object {
        const val TOKEN_X_PATHS = "/api/innsyn/**"

        // Swagger is only enabled in dev. In prod these paths give 404.
        val UNAUTHENTICATED_PATHS =
            listOf(
                "/internal/health",
                "/internal/health/**",
                "/internal/info",
                "/internal/prometheus",
                "/error",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**",
            )
    }

    @Bean
    @Order(1)
    fun tokenXFilterChain(
        http: HttpSecurity,
        environment: Environment,
    ): SecurityFilterChain =
        http
            .securityMatcher(TOKEN_X_PATHS)
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { it.decoder(tokenXJwtDecoder(TokenXAuthProperties.from(environment))) }
            }.build()

    @Bean
    @Order(2)
    fun azureFilterChain(
        http: HttpSecurity,
        environment: Environment,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(*UNAUTHENTICATED_PATHS.toTypedArray()).permitAll()
                // DevOnlyAdminController and MockDataController only exist in dev.
                if (environment.acceptsProfiles(Profiles.of("dev"))) {
                    it.requestMatchers("/internal/dev/**", "/mockdata/**").permitAll()
                }
                // UnprotectedDataFeeder only exists in dev and local.
                if (environment.acceptsProfiles(Profiles.of("dev", "local"))) {
                    it.requestMatchers(HttpMethod.POST, "/internal/manualfeed").permitAll()
                }
                it.anyRequest().authenticated()
            }.oauth2ResourceServer { resourceServer ->
                resourceServer.jwt { it.decoder(azureJwtDecoder(AzureAuthProperties.from(environment))) }
            }.build()
}
