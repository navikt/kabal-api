package no.nav.klage.oppgave.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.CommonsRequestLoggingFilter

@Configuration
class RequestLoggingConfig {
    @Bean
    fun logFilter(): CommonsRequestLoggingFilter {
        val filter = CommonsRequestLoggingFilter()
        filter.isIncludeQueryString = true
        filter.isIncludePayload = false
        filter.isIncludeHeaders = false
        filter.isIncludeClientInfo = true
        return filter
    }
}
