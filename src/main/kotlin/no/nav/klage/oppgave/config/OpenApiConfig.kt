package no.nav.klage.oppgave.config

import no.nav.klage.oppgave.api.controller.BehandlingDetaljerController
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import springfox.documentation.builders.PathSelectors.regex
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.service.Tag
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket

@Configuration
class OpenApiConfig {

    @Bean
    fun apiExternal(): Docket {
        return Docket(DocumentationType.OAS_30)
            .select()
            .paths(regex(".*api.*"))
            .build()
            .pathMapping("/")
            .groupName("external")
            .genericModelSubstitutes(ResponseEntity::class.java)
            .tags(Tag("kabal-api-external", "Eksternt api for Kabal"))
    }

    @Bean
    fun apiInternal(): Docket {
        return Docket(DocumentationType.OAS_30)
            .select()
            .apis(RequestHandlerSelectors.basePackage(BehandlingDetaljerController::class.java.packageName))
            .build()
            .pathMapping("/")
            .groupName("internal")
            .genericModelSubstitutes(ResponseEntity::class.java)
            .tags(Tag("kabal-api", "API for saksbehandlere ved klageinstansen"))
    }

}
