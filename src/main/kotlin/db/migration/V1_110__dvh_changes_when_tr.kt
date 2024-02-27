package db.migration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.oppgave.domain.kafka.BehandlingState
import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.util.*


class V1_110__dvh_changes_when_tr: BaseJavaMigration() {
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
                    select ke.id, ke.json_payload, b.utfall_id, b.dato_behandling_avsluttet_av_saksbehandler::TIMESTAMP::DATE
                    from klage.kafka_event ke, klage.behandling b
                    where ke.behandling_id = b.id
                    and ke.type = 'STATS_DVH'
                    and ke.json_payload like '%MOTTATT_FRA_TR%'
                    and ke.created < '2023-09-19'
                    """)
                .use { rows ->
                while (rows.next()) {
                    val kafkaEventId = rows.getObject(1, UUID::class.java)
                    val jsonPayload = rows.getString(2)
                    val utfallId = rows.getString(3)
                    val vedtakDate = rows.getDate(4)

                    if (utfallId.isNullOrEmpty()) {
                        //ignore this row
                        continue
                    }

                    val statistikkTilDVH = jacksonObjectMapper().readValue(jsonPayload, StatistikkTilDVH::class.java)

                    val modifiedVersion = statistikkTilDVH.copy(
                        resultat = Utfall.of(utfallId).navn,
                        vedtaksdato = vedtakDate.toLocalDate(),
                        behandlingStatus = BehandlingState.AVSLUTTET,
                    )

                    preparedStatement.setString(1, jacksonObjectMapper().writeValueAsString(modifiedVersion))
                    preparedStatement.setObject(2, kafkaEventId)

                    preparedStatement.executeUpdate()
                }
            }
        }
    }
}