package no.nav.klage.oppgave.db

import com.ninjasquad.springmockk.MockkBean
import no.nav.klage.oppgave.domain.behandling.subentities.Saksdokument
import no.nav.klage.oppgave.util.TokenUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.ResultSet


@ActiveProfiles("local")
@DataJpaTest
class FlywayMigrationTest : PostgresIntegrationTestBase() {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    //Because of Hibernate Envers and our setup for audit logs.
    @MockkBean
    lateinit var tokenUtil: TokenUtil

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
