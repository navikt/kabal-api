package db.migration

import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.time.LocalDateTime
import java.util.*

class V168__dvh_pesys_fix_migrated_anker : BaseJavaMigration() {
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
                      and ke.kilde_referanse in (
'31931181',
'34567970',
'43721330',
'44495954',
'44719321',
'44763744',
'49228807',
'49255888',
'49344384',
'49402593')
                    """
            )
                .use { rows ->
                    while (rows.next()) {
                        val kafkaEventId = rows.getObject(1, UUID::class.java)
                        val jsonPayload = rows.getString(2)

                        val statistikkTilDVH =
                            ourJacksonObjectMapper().readValue(jsonPayload, StatistikkTilDVH::class.java)

                        val modifiedVersion = when (statistikkTilDVH.behandlingId) {
                            "24587668" -> statistikkTilDVH.copy(behandlingId = "49767354", tekniskTid = LocalDateTime.now())
                            "26576591" -> statistikkTilDVH.copy(behandlingId = "48401201", tekniskTid = LocalDateTime.now())
                            "31072898" -> statistikkTilDVH.copy(behandlingId = "49106144", tekniskTid = LocalDateTime.now())
                            "32007487" -> statistikkTilDVH.copy(behandlingId = "49775672", tekniskTid = LocalDateTime.now())
                            "32154187" -> statistikkTilDVH.copy(behandlingId = "49433993", tekniskTid = LocalDateTime.now())
                            "32117968" -> statistikkTilDVH.copy(behandlingId = "49053570", tekniskTid = LocalDateTime.now())
                            "33642318" -> statistikkTilDVH.copy(behandlingId = "49276364", tekniskTid = LocalDateTime.now())
                            "33827126" -> statistikkTilDVH.copy(behandlingId = "48399557", tekniskTid = LocalDateTime.now())
                            "33901598" -> statistikkTilDVH.copy(behandlingId = "48489928", tekniskTid = LocalDateTime.now())
                            "34269038" -> statistikkTilDVH.copy(behandlingId = "49299115", tekniskTid = LocalDateTime.now())
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