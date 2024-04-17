package db.migration

import no.nav.klage.oppgave.domain.kafka.BehandlingState
import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.util.*


class V120__dvh_set_TR_enhet : BaseJavaMigration() {
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
                    SELECT ke.id, ke.json_payload 
                    FROM klage.kafka_event ke, klage.behandling b WHERE ke.kilde_referanse IN
                    (SELECT dvh_referanse FROM klage.behandling WHERE type_id = '3'
                    AND dato_behandling_avsluttet IS NOT NULL
                    AND kjennelse_mottatt IS NOT NULL
                    AND ny_behandling_ka IS NULL
                    AND utfall_id != '12')
                    AND ke.json_payload LIKE '%AVSLUTTET%'
                    AND ke.type = 'STATS_DVH'
                    AND ke.json_payload NOT LIKE '%TR0000%'
                    AND ke.behandling_id = b.id
                    AND b.type_id = '3'
                    """
            )
                .use { rows ->
                    while (rows.next()) {
                        val kafkaEventId = rows.getObject(1, UUID::class.java)
                        val jsonPayload = rows.getString(2)

                        val statistikkTilDVH =
                            ourJacksonObjectMapper().readValue(jsonPayload, StatistikkTilDVH::class.java)

                        if (statistikkTilDVH.behandlingStatus == BehandlingState.AVSLUTTET && statistikkTilDVH.ansvarligEnhetKode == null) {
                            val modifiedVersion = statistikkTilDVH.copy(
                                ansvarligEnhetKode = "TR0000",
                            )

                            preparedStatement.setString(1, ourJacksonObjectMapper().writeValueAsString(modifiedVersion))
                            preparedStatement.setObject(2, kafkaEventId)

                            preparedStatement.executeUpdate()
                        } else {
                            System.err.println("Unexpected dvhstat, eventId: ${statistikkTilDVH.eventId}")
                        }
                    }
                }
        }
    }
}