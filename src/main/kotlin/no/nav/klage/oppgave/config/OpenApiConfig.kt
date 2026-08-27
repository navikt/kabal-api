package no.nav.klage.oppgave.config

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import no.nav.klage.dokument.api.controller.DokumentUnderArbeidController
import no.nav.klage.innsyn.api.controller.InnsynController
import no.nav.klage.kaptein.api.controller.KapteinController
import no.nav.klage.oppgave.api.controller.BehandlingDetaljerController
import no.nav.klage.oppgave.api.controller.external.ExternalApiController
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@SecurityRequirement(name = "bearerAuth")
@SecurityScheme(
    type = SecuritySchemeType.HTTP,
    bearerFormat = "jwt",
    name = "bearerAuth",
    scheme = "bearer",
    `in` = SecuritySchemeIn.HEADER,
)
class OpenApiConfig {
    @Bean
    fun apiInternal(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .packagesToScan(BehandlingDetaljerController::class.java.packageName)
            .group("internal")
            .pathsToMatch("/**")
            .pathsToExclude("/api/**")
            .build()

    @Bean
    fun apiInternalDokumenterUnderArbeid(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .packagesToScan(DokumentUnderArbeidController::class.java.packageName)
            .group("internal-documents")
            .pathsToMatch("/**")
            .build()

    @Bean
    fun apiInternalKaptein(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .packagesToScan(KapteinController::class.java.packageName)
            .group("internal-kaptein")
            .pathsToMatch("/**")
            .build()

    @Bean
    fun apiInternalInnsyn(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .packagesToScan(InnsynController::class.java.packageName)
            .group("internal-innsyn")
            .pathsToMatch("/**")
            .build()

    @Bean
    fun apiExternal(): GroupedOpenApi =
        GroupedOpenApi
            .builder()
            .packagesToScan(ExternalApiController::class.java.packageName)
            .group("external")
            .pathsToMatch("/api/**")
            .build()
}
