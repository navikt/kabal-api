package no.nav.klage.oppgave

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.library.Architectures.layeredArchitecture


@AnalyzeClasses(packages = ["no.nav.klage"], importOptions = [ImportOption.DoNotIncludeTests::class])
class LayeredArchitectureTest {

    fun kabalApiLayeredArchitecture() = layeredArchitecture().consideringAllDependencies()
        .layer("Controllers").definedBy("no.nav.klage.oppgave.api.controller..", "no.nav.klage.dokument.api.controller..")
        .layer("ApiMappers").definedBy("no.nav.klage.oppgave.api.mapper..", "no.nav.klage.dokument.api.mapper..")
        .layer("View").definedBy("no.nav.klage.oppgave.api.view..", "no.nav.klage.dokument.api.view..")
        .layer("Services").definedBy("no.nav.klage.oppgave.service..", "no.nav.klage.dokument.service..")
        .layer("Repositories").definedBy("no.nav.klage.oppgave.repositories..", "no.nav.klage.dokument.repositories..")
        .layer("Clients").definedBy("no.nav.klage.oppgave.clients..", "no.nav.klage.dokument.clients..")
        .layer("Config").definedBy("no.nav.klage.oppgave.config..", "no.nav.klage.dokument.config..")
        .layer("Domain").definedBy("no.nav.klage.oppgave.domain..", "no.nav.klage.dokument.domain..")
        .layer("Eventlisteners").definedBy("no.nav.klage.oppgave.eventlisteners..", "no.nav.klage.dokument.eventlisteners..")
        .layer("Util").definedBy("no.nav.klage.oppgave.util..", "no.nav.klage.dokument.util..")
        .layer("Exceptions").definedBy("no.nav.klage.oppgave.exceptions..", "no.nav.klage.dokument.exceptions..")
        .layer("Gateway").definedBy("no.nav.klage.oppgave.gateway", "no.nav.klage.dokument.gateway")

    @ArchTest
    val layer_dependencies_are_respected_for_controllers: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("Controllers").mayOnlyBeAccessedByLayers("Config")

    @ArchTest
    val layer_dependencies_are_respected_for_apimappers: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("ApiMappers").mayOnlyBeAccessedByLayers("Controllers", "Config", "Services")

    @ArchTest
    val layer_dependencies_are_respected_for_view: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("View").mayOnlyBeAccessedByLayers("Controllers", "Services", "Config", "ApiMappers", "Exceptions", "Util", "Domain")

    @ArchTest
    val layer_dependencies_are_respected_for_services: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("Services").mayOnlyBeAccessedByLayers("Controllers", "Config", "Eventlisteners", "ApiMappers")

    @ArchTest
    val layer_dependencies_are_respected_for_persistence: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("Repositories")
        .mayOnlyBeAccessedByLayers("Services", "Controllers", "Config", "Eventlisteners", "ApiMappers")

    @ArchTest
    val layer_dependencies_are_respected_for_clients: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("Clients")
        .mayOnlyBeAccessedByLayers("Services", "Repositories", "Config", "Controllers", "Util", "ApiMappers", "Gateway", "Eventlisteners")

    @ArchTest
    val layer_dependencies_are_respected_for_eventlisteners: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("Eventlisteners").mayOnlyBeAccessedByLayers("Config", "Services")

}