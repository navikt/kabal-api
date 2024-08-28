package db.migration

import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.time.LocalDateTime
import java.util.*

class V146__dvh_pesys_fix_teknisk_tid : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val preparedStatement = context.connection.prepareStatement(
            """
                update klage.kafka_event
                    set json_payload = ?
                    where id = ?
            """.trimIndent()
        )

        context.connection.createStatement().use { select ->
            select.executeQuery(
                """
                    select ke.id, ke.json_payload
                    from klage.kafka_event ke
                    where ke.type = 'STATS_DVH'
                      and ke.kilde_referanse in ('66053136', '68377705', '68377717', '68377745', '68377764', '68377816', '68377844', '68378050')
                    """
            )
                .use { rows ->
                    while (rows.next()) {
                        val kafkaEventId = rows.getObject(1, UUID::class.java)
                        val jsonPayload = rows.getString(2)

                        val statistikkTilDVH =
                            ourJacksonObjectMapper().readValue(jsonPayload, StatistikkTilDVH::class.java)

                        val modifiedVersion = when (statistikkTilDVH.behandlingId) {
                            "66053136" -> statistikkTilDVH.copy(tekniskTid = LocalDateTime.now())
                            "68377705" -> statistikkTilDVH.copy(tekniskTid = LocalDateTime.now())
                            "68377717" -> statistikkTilDVH.copy(tekniskTid = LocalDateTime.now())
                            "68377745" -> statistikkTilDVH.copy(tekniskTid = LocalDateTime.now())
                            "68377764" -> statistikkTilDVH.copy(tekniskTid = LocalDateTime.now())
                            "68377816" -> statistikkTilDVH.copy(tekniskTid = LocalDateTime.now())
                            "68377844" -> statistikkTilDVH.copy(tekniskTid = LocalDateTime.now())
                            "68378050" -> statistikkTilDVH.copy(tekniskTid = LocalDateTime.now())
                            else -> throw RuntimeException("Unknown behandlingId: ${statistikkTilDVH.behandlingId}")
                        }

                        preparedStatement.setString(1, ourJacksonObjectMapper().writeValueAsString(modifiedVersion))
                        preparedStatement.setObject(2, kafkaEventId)

                        preparedStatement.executeUpdate()
                    }

                }
        }
    }
}