package no.nav.klage.oppgave

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.library.Architectures.layeredArchitecture


@AnalyzeClasses(packages = ["no.nav.klage.oppgave"], importOptions = [ImportOption.DoNotIncludeTests::class])
class LayeredArchitectureTest {

    fun kabalApiLayeredArchitecture() = layeredArchitecture()
        .layer("Controllers").definedBy("no.nav.klage.oppgave.api.controller")
        .layer("ApiMappers").definedBy("no.nav.klage.oppgave.api.mapper")
        .layer("View").definedBy("no.nav.klage.oppgave.api.view")
        .layer("Services").definedBy("no.nav.klage.oppgave.service..")
        .layer("Repositories").definedBy("no.nav.klage.oppgave.repositories..")
        .layer("Clients").definedBy("no.nav.klage.oppgave.clients..")
        .layer("Config").definedBy("no.nav.klage.oppgave.config..")
        .layer("Domain").definedBy("no.nav.klage.oppgave.domain..")
        .layer("Eventlisteners").definedBy("no.nav.klage.oppgave.eventlisteners..")
        .layer("Util").definedBy("no.nav.klage.oppgave.util..")
        .layer("Exceptions").definedBy("no.nav.klage.oppgave.exceptions..")
        .layer("Gateway").definedBy("no.nav.klage.oppgave.gateway")

    @ArchTest
    val layer_dependencies_are_respected_for_controllers: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("Controllers").mayOnlyBeAccessedByLayers("Config")

    @ArchTest
    val layer_dependencies_are_respected_for_apimappers: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("ApiMappers").mayOnlyBeAccessedByLayers("Controllers", "Config")

    @ArchTest
    val layer_dependencies_are_respected_for_view: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("View").mayOnlyBeAccessedByLayers("Controllers", "Services", "Config", "ApiMappers")

    @ArchTest
    val layer_dependencies_are_respected_for_services: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("Services").mayOnlyBeAccessedByLayers("Controllers", "Config", "Eventlisteners", "ApiMappers")

    @ArchTest
    val layer_dependencies_are_respected_for_persistence: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("Repositories").mayOnlyBeAccessedByLayers("Services", "Controllers", "Config", "Eventlisteners")

    @ArchTest
    val layer_dependencies_are_respected_for_clients: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("Clients")
        .mayOnlyBeAccessedByLayers("Services", "Repositories", "Config", "Controllers", "Util", "ApiMappers", "Gateway")

    @ArchTest
    val layer_dependencies_are_respected_for_eventlisteners: ArchRule = kabalApiLayeredArchitecture()
        .whereLayer("Eventlisteners").mayOnlyBeAccessedByLayers("Config")

}