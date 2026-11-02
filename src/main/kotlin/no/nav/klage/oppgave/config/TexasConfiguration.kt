package no.nav.klage.oppgave.config

import no.nav.klage.auth.TexasClient
import no.nav.klage.auth.TexasProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration
class TexasConfiguration {
    @Bean
    fun texasClient(environment: Environment): TexasClient = TexasClient(TexasProperties.from(environment))
}
