package db.migration

import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.util.*


class V124__dvh_sakid_and_ytelse : BaseJavaMigration() {
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
                    select ke.id, ke.json_payload, b.sak_fagsak_id, b.ytelse_id
                    from klage.kafka_event ke,
                         klage.behandling b
                    where ke.type = 'STATS_DVH'
                      and ke.behandling_id = b.id
                    """
            )
                .use { rows ->
                    while (rows.next()) {
                        val kafkaEventId = rows.getObject(1, UUID::class.java)
                        val jsonPayload = rows.getString(2)
                        val sakFagsakId = rows.getString(3)
                        val ytelseId = rows.getString(4)

                        val statistikkTilDVH =
                            ourJacksonObjectMapper().readValue(jsonPayload, StatistikkTilDVH::class.java)

                        if (statistikkTilDVH.resultat == "Stadfestelse") {
                            val modifiedVersion = statistikkTilDVH.copy(
                                opprinneligFagsakId = sakFagsakId,
                                ytelseType = Ytelse.of(ytelseId).name,
                            )

                            preparedStatement.setString(1, ourJacksonObjectMapper().writeValueAsString(modifiedVersion))
                            preparedStatement.setObject(2, kafkaEventId)

                            preparedStatement.executeUpdate()
                        }
                    }
                }
        }
    }
}