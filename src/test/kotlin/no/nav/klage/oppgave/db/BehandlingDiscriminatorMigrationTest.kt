package no.nav.klage.oppgave.db

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.util.UUID

@Testcontainers
class BehandlingDiscriminatorMigrationTest {
    @Test
    fun `V252 consolidates both current and audited discriminators and is idempotent`() {
        postgres.createConnection("").use { connection ->
            createMinimalTables(connection)
            insertFixtures(connection)

            val liveBefore = readRows(connection = connection, table = "klage.behandling")
            val auditBefore = readRows(connection = connection, table = "klage.behandling_aud")

            val migration = ClassPathResource("db/migration/V252__consolidate_gjenopptak_and_omgjoeringskrav.sql")
            ScriptUtils.executeSqlScript(connection, migration)

            assertThat(readRows(connection = connection, table = "klage.behandling"))
                .containsExactlyElementsOf(liveBefore.map(::consolidated))
            assertThat(readRows(connection = connection, table = "klage.behandling_aud"))
                .containsExactlyElementsOf(auditBefore.map(::consolidated))
            assertThat(readRows(connection = connection, table = "klage.behandling").map { it.discriminator })
                .contains("gjenopptak", "omgjoeringskrav", "klage")
            assertThat(readRows(connection = connection, table = "klage.behandling_aud").map { it.discriminator })
                .contains("gjenopptak", "omgjoeringskrav", "klage")

            val liveAfterFirstRun = readRows(connection = connection, table = "klage.behandling")
            val auditAfterFirstRun = readRows(connection = connection, table = "klage.behandling_aud")
            ScriptUtils.executeSqlScript(connection, migration)

            assertThat(readRows(connection = connection, table = "klage.behandling"))
                .containsExactlyElementsOf(liveAfterFirstRun)
            assertThat(readRows(connection = connection, table = "klage.behandling_aud"))
                .containsExactlyElementsOf(auditAfterFirstRun)
            assertThat(readRows(connection = connection, table = "klage.behandling_aud"))
                .anySatisfy { row ->
                    assertThat(row.id).isEqualTo(AUDIT_ONLY_ID.toString())
                    assertThat(row.discriminator).isEqualTo("gjenopptak")
                    assertThat(row.kildeReferanse).isEqualTo("historical-only")
                }
            assertThat(readRows(connection = connection, table = "klage.behandling").map { it.id })
                .doesNotContain(AUDIT_ONLY_ID.toString())
        }
    }

    private fun createMinimalTables(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("CREATE SCHEMA klage")
            statement.execute(
                """
                CREATE TABLE klage.behandling (
                    id UUID PRIMARY KEY,
                    behandling_type TEXT NOT NULL,
                    previous_behandling_id UUID,
                    paaanket_vedtaks_dato DATE,
                    forsterket_rett BOOLEAN,
                    kilde_referanse TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE klage.behandling_aud (
                    id UUID NOT NULL,
                    rev INTEGER NOT NULL,
                    revtype SMALLINT,
                    behandling_type TEXT NOT NULL,
                    previous_behandling_id UUID,
                    paaanket_vedtaks_dato DATE,
                    forsterket_rett BOOLEAN,
                    kilde_referanse TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private fun insertFixtures(connection: Connection) {
        connection
            .prepareStatement(
                """
                INSERT INTO klage.behandling
                    (id, behandling_type, previous_behandling_id, paaanket_vedtaks_dato, forsterket_rett, kilde_referanse)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                OLD_DISCRIMINATORS.forEachIndexed { index, discriminator ->
                    statement.setObject(
                        1,
                        UUID.fromString("00000000-0000-0000-0000-${(index + 1).toString().padStart(length = 12, padChar = '0')}"),
                    )
                    statement.setString(2, discriminator)
                    statement.setObject(3, PREVIOUS_ID)
                    statement.setObject(4, java.sql.Date.valueOf("2022-03-04"))
                    statement.setBoolean(5, index % 2 == 0)
                    statement.setString(6, "source-$index")
                    statement.addBatch()
                }
                NEW_DISCRIMINATORS.forEachIndexed { index, discriminator ->
                    statement.setObject(
                        1,
                        UUID.fromString("00000000-0000-0000-0000-${(index + 5).toString().padStart(length = 12, padChar = '0')}"),
                    )
                    statement.setString(2, discriminator)
                    statement.setObject(3, PREVIOUS_ID)
                    statement.setObject(4, java.sql.Date.valueOf("2022-03-04"))
                    statement.setBoolean(5, index == 0)
                    statement.setString(6, "already-consolidated-$index")
                    statement.addBatch()
                }
                statement.setObject(1, UNRELATED_ID)
                statement.setString(2, "klage")
                statement.setObject(3, PREVIOUS_ID)
                statement.setObject(4, java.sql.Date.valueOf("2021-06-07"))
                statement.setBoolean(5, false)
                statement.setString(6, "unrelated")
                statement.addBatch()
                statement.executeBatch()
            }

        connection
            .prepareStatement(
                """
                INSERT INTO klage.behandling_aud
                    (id, rev, revtype, behandling_type, previous_behandling_id, paaanket_vedtaks_dato, forsterket_rett, kilde_referanse)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                OLD_DISCRIMINATORS.forEachIndexed { index, discriminator ->
                    statement.setObject(
                        1,
                        UUID.fromString("10000000-0000-0000-0000-${(index + 1).toString().padStart(length = 12, padChar = '0')}"),
                    )
                    statement.setInt(2, index + 1)
                    statement.setShort(3, 0)
                    statement.setString(4, discriminator)
                    statement.setObject(5, PREVIOUS_ID)
                    statement.setObject(6, java.sql.Date.valueOf("2020-08-09"))
                    statement.setBoolean(7, index % 2 == 1)
                    statement.setString(8, "audited-$index")
                    statement.addBatch()
                }
                NEW_DISCRIMINATORS.forEachIndexed { index, discriminator ->
                    statement.setObject(
                        1,
                        UUID.fromString("10000000-0000-0000-0000-${(index + 5).toString().padStart(length = 12, padChar = '0')}"),
                    )
                    statement.setInt(2, index + 5)
                    statement.setShort(3, 0)
                    statement.setString(4, discriminator)
                    statement.setObject(5, PREVIOUS_ID)
                    statement.setObject(6, java.sql.Date.valueOf("2020-08-09"))
                    statement.setBoolean(7, index == 0)
                    statement.setString(8, "already-audited-consolidated-$index")
                    statement.addBatch()
                }
                statement.setObject(1, UNRELATED_ID)
                statement.setInt(2, 10)
                statement.setShort(3, 0)
                statement.setString(4, "klage")
                statement.setObject(5, PREVIOUS_ID)
                statement.setObject(6, java.sql.Date.valueOf("2019-10-11"))
                statement.setBoolean(7, true)
                statement.setString(8, "unrelated-audit")
                statement.addBatch()
                statement.setObject(1, AUDIT_ONLY_ID)
                statement.setInt(2, 11)
                statement.setShort(3, 0)
                statement.setString(4, OLD_DISCRIMINATORS.first())
                statement.setObject(5, PREVIOUS_ID)
                statement.setObject(6, java.sql.Date.valueOf("2018-12-13"))
                statement.setBoolean(7, false)
                statement.setString(8, "historical-only")
                statement.addBatch()
                statement.executeBatch()
            }
    }

    private fun readRows(
        connection: Connection,
        table: String,
    ): List<Snapshot> =
        connection.createStatement().use { statement ->
            statement
                .executeQuery(
                    """
                    SELECT id::text, behandling_type, previous_behandling_id::text,
                           paaanket_vedtaks_dato::text, forsterket_rett, kilde_referanse
                    FROM $table
                    ORDER BY id, ${if (table.endsWith("_aud")) "rev" else "id"}
                    """.trimIndent(),
                ).use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                Snapshot(
                                    id = resultSet.getString(1),
                                    discriminator = resultSet.getString(2),
                                    previousBehandlingId = resultSet.getString(3),
                                    paaanketVedtaksdato = resultSet.getString(4),
                                    forsterketRett = resultSet.getBoolean(5),
                                    kildeReferanse = resultSet.getString(6),
                                ),
                            )
                        }
                    }
                }
        }

    private fun consolidated(row: Snapshot): Snapshot =
        row.copy(
            discriminator =
                when (row.discriminator) {
                    "gjenopptak-based-on-journalpost", "gjenopptak-based-on-kabal-behandling" -> {
                        "gjenopptak"
                    }

                    "omgjoeringskrav-based-on-journalpost", "omgjoeringskrav-based-on-kabal-behandling" -> {
                        "omgjoeringskrav"
                    }

                    else -> {
                        row.discriminator
                    }
                },
        )

    private data class Snapshot(
        val id: String,
        val discriminator: String,
        val previousBehandlingId: String?,
        val paaanketVedtaksdato: String?,
        val forsterketRett: Boolean,
        val kildeReferanse: String,
    )

    companion object {
        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:18.0")
                .withDatabaseName("discriminator_migration")
        private val PREVIOUS_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        private val UNRELATED_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        private val AUDIT_ONLY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")
        private val OLD_DISCRIMINATORS =
            listOf(
                "gjenopptak-based-on-journalpost",
                "gjenopptak-based-on-kabal-behandling",
                "omgjoeringskrav-based-on-journalpost",
                "omgjoeringskrav-based-on-kabal-behandling",
            )
        private val NEW_DISCRIMINATORS = listOf("gjenopptak", "omgjoeringskrav")
    }
}
