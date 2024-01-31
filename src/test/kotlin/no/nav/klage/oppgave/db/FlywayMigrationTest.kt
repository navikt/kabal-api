package no.nav.klage.oppgave.db

import no.nav.klage.oppgave.domain.klage.Saksdokument
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.ResultSet


@ActiveProfiles("local")
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FlywayMigrationTest {

    companion object {
        @Container
        @JvmField
        val postgreSQLContainer: TestPostgresqlContainer = TestPostgresqlContainer.instance
    }

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun flyway_should_run() {
        val saksdokumenter: List<Saksdokument> = jdbcTemplate.query(
            "SELECT * FROM klage.saksdokument"
        ) { rs: ResultSet, _: Int ->
            Saksdokument(
                journalpostId = rs.getString("journalpost_id"),
                dokumentInfoId = rs.getString("dokument_info_id")
            )
        }

        assertThat(saksdokumenter).hasSize(0)
    }

}
