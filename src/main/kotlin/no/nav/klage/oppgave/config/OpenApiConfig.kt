package no.nav.klage.oppgave.config

import no.nav.klage.dokument.api.controller.DokumentUnderArbeidController
import no.nav.klage.oppgave.api.controller.BehandlingDetaljerController
import no.nav.klage.oppgave.api.controller.ExternalApiController
import org.springdoc.core.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun apiInternal(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .packagesToScan(BehandlingDetaljerController::class.java.packageName)
            .group("internal")
            .pathsToMatch("/**")
            .pathsToExclude("/api/**")
            .build()
    }

    @Bean
    fun apiInternalDokumenterUnderArbeid(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .packagesToScan(DokumentUnderArbeidController::class.java.packageName)
            .group("internal-documents")
            .pathsToMatch("/**")
            .build()
    }

    @Bean
    fun apiExternal(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .packagesToScan(ExternalApiController::class.java.packageName)
            .group("external")
            .pathsToMatch("/api/**")
            .build()
    }
}
