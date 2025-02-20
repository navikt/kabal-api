package db.migration

import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.time.LocalDateTime
import java.util.*

class V193__dvh_pesys_fix_migrated_anke_i_tr : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val preparedStatement = context.connection.prepareStatement(
            """
                update klage.kafka_event
                    set json_payload = ?, status_id = ?
                    where id = ?
            """.trimIndent()
        )

        context.connection.createStatement().use { select ->
            select.executeQuery(
                """
                    select ke.id, ke.json_payload
                    from klage.kafka_event ke
                    where ke.type = 'STATS_DVH'
                      and ke.behandling_id = 'f49cf467-7cd5-4db0-b8ad-8ce9181ce814'
                    """
            )
                .use { rows ->
                    while (rows.next()) {
                        val kafkaEventId = rows.getObject(1, UUID::class.java)
                        val jsonPayload = rows.getString(2)

                        val statistikkTilDVH =
                            ourJacksonObjectMapper().readValue(jsonPayload, StatistikkTilDVH::class.java)

                        val modifiedVersion = when (statistikkTilDVH.behandlingId) {
                            "36263910" -> statistikkTilDVH.copy(behandlingId = "48270708", tekniskTid = LocalDateTime.now())
                            else -> throw RuntimeException("Unknown behandlingId: ${statistikkTilDVH.behandlingId}")
                        }

                        preparedStatement.setString(1, ourJacksonObjectMapper().writeValueAsString(modifiedVersion))
                        preparedStatement.setObject(2,"IKKE_SENDT")
                        preparedStatement.setObject(3, kafkaEventId)

                        preparedStatement.executeUpdate()
                    }

                }
        }
    }
}