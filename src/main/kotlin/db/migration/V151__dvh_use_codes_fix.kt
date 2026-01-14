package db.migration

import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.util.getSecureLogger
import no.nav.klage.oppgave.util.ourJsonMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.time.LocalDateTime
import java.util.*

class V151__dvh_use_codes_fix : BaseJavaMigration() {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val secureLogger = getSecureLogger()
    }

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
                    and ke.json_payload like '%NY_ANKEBEHANDLING_I_KA%'
                    order by ke.created
                """
            )
                .use { rows ->
                    while (rows.next()) {
                        try {
                            val kafkaEventId = rows.getObject(1, UUID::class.java)
                            val jsonPayload = rows.getString(2)

                            val statistikkTilDVH =
                                ourJsonMapper().readValue(jsonPayload, StatistikkTilDVH::class.java)

                            val modifiedVersion = statistikkTilDVH.copy(
                                resultat = Utfall.entries.find { it.navn == statistikkTilDVH.resultat }?.name
                                    ?: statistikkTilDVH.resultat,
                                behandlingType = Type.entries.find { it.navn == statistikkTilDVH.behandlingType }!!.name,
                                tekniskTid = LocalDateTime.now()
                            )

                            preparedStatement.setString(1, ourJsonMapper().writeValueAsString(modifiedVersion))
                            preparedStatement.setObject(2, kafkaEventId)

                            preparedStatement.executeUpdate()
                        } catch (e: Exception) {
                            secureLogger.warn(
                                "Failed to update kafka event with id ${rows.getObject(1, UUID::class.java)}",
                                e
                            )
                        }
                    }
                }
        }
    }
}