package no.nav.klage.oppgave.db

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy

abstract class PostgresIntegrationTestBase {

    companion object {
        private val postgres = PostgreSQLContainer("postgres:15.4").apply {
            waitingFor(HostPortWaitStrategy())
            withReuse(true)
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}