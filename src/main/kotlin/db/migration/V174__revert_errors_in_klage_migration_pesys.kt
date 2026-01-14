package db.migration

import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.util.*

class V174__revert_errors_in_klage_migration_pesys : BaseJavaMigration() {
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
'68341966',
'68335904',
'68378050',
'68071016',
'68357300',
'68386349',
'68374308',
'68398156',
'68399202',
'68380595',
'68461519',
'66071455',
'68435624',
'62376026',
'68387440',
'68379786',
'65197937',
'68346480',
'68383245',
'68378289',
'67885446',
'68356961',
'68374374',
'68378343',
'68380026',
'68396547',
'68397995',
'68401033',
'68409038',
'68441630',
'68510878',
'68399209',
'66234730',
'66222978',
'68365486',
'67902279',
'66197891',
'68389702',
'68333057',
'67892362')
                    """
            )
                .use { rows ->
                    while (rows.next()) {
                        val kafkaEventId = rows.getObject(1, UUID::class.java)
                        val jsonPayload = rows.getString(2)

                        val statistikkTilDVH =
                            jacksonObjectMapper().readValue(jsonPayload, StatistikkTilDVH::class.java)

                        //Only applicable for KLAGE entries.
                        if (statistikkTilDVH.behandlingType == "KLAGE") {
                            val modifiedVersion = when (statistikkTilDVH.behandlingId) {
                                "49861032" -> statistikkTilDVH.copy(behandlingId = "49090340", tekniskTid = LocalDateTime.now())
                                "49414999" -> statistikkTilDVH.copy(behandlingId = "48462749", tekniskTid = LocalDateTime.now())
                                "49231239" -> statistikkTilDVH.copy(behandlingId = "48494946", tekniskTid = LocalDateTime.now())
                                "49473997" -> statistikkTilDVH.copy(behandlingId = "48402252", tekniskTid = LocalDateTime.now())
                                "49422510" -> statistikkTilDVH.copy(behandlingId = "49072175", tekniskTid = LocalDateTime.now())
                                "49474102" -> statistikkTilDVH.copy(behandlingId = "48455436", tekniskTid = LocalDateTime.now())
                                "49873682" -> statistikkTilDVH.copy(behandlingId = "48390434", tekniskTid = LocalDateTime.now())
                                "49767613" -> statistikkTilDVH.copy(behandlingId = "48828283", tekniskTid = LocalDateTime.now())
                                "49893939" -> statistikkTilDVH.copy(behandlingId = "48815994", tekniskTid = LocalDateTime.now())
                                "49873664" -> statistikkTilDVH.copy(behandlingId = "47204549", tekniskTid = LocalDateTime.now())
                                "49767685" -> statistikkTilDVH.copy(behandlingId = "49090915", tekniskTid = LocalDateTime.now())
                                "49860029" -> statistikkTilDVH.copy(behandlingId = "47222601", tekniskTid = LocalDateTime.now())
                                "49851697" -> statistikkTilDVH.copy(behandlingId = "49256808", tekniskTid = LocalDateTime.now())
                                "49478752" -> statistikkTilDVH.copy(behandlingId = "44819209", tekniskTid = LocalDateTime.now())
                                "49422775" -> statistikkTilDVH.copy(behandlingId = "49165749", tekniskTid = LocalDateTime.now())
                                "49483934" -> statistikkTilDVH.copy(behandlingId = "48440076", tekniskTid = LocalDateTime.now())
                                "49458067" -> statistikkTilDVH.copy(behandlingId = "46959012", tekniskTid = LocalDateTime.now())
                                "49780534" -> statistikkTilDVH.copy(behandlingId = "49074672", tekniskTid = LocalDateTime.now())
                                "49771665" -> statistikkTilDVH.copy(behandlingId = "48816129", tekniskTid = LocalDateTime.now())
                                "49851804" -> statistikkTilDVH.copy(behandlingId = "48823408", tekniskTid = LocalDateTime.now())
                                "49485867" -> statistikkTilDVH.copy(behandlingId = "47288758", tekniskTid = LocalDateTime.now())
                                "49467280" -> statistikkTilDVH.copy(behandlingId = "48818642", tekniskTid = LocalDateTime.now())
                                "49861012" -> statistikkTilDVH.copy(behandlingId = "48431978", tekniskTid = LocalDateTime.now())
                                "49476251" -> statistikkTilDVH.copy(behandlingId = "48453744", tekniskTid = LocalDateTime.now())
                                "49875869" -> statistikkTilDVH.copy(behandlingId = "48425693", tekniskTid = LocalDateTime.now())
                                "49434808" -> statistikkTilDVH.copy(behandlingId = "49106286", tekniskTid = LocalDateTime.now())
                                "49906019" -> statistikkTilDVH.copy(behandlingId = "49080458", tekniskTid = LocalDateTime.now())
                                "49780761" -> statistikkTilDVH.copy(behandlingId = "49076925", tekniskTid = LocalDateTime.now())
                                "49776013" -> statistikkTilDVH.copy(behandlingId = "49215677", tekniskTid = LocalDateTime.now())
                                "49780600" -> statistikkTilDVH.copy(behandlingId = "48815757", tekniskTid = LocalDateTime.now())
                                "49906200" -> statistikkTilDVH.copy(behandlingId = "49110399", tekniskTid = LocalDateTime.now())
                                "49903520" -> statistikkTilDVH.copy(behandlingId = "48824777", tekniskTid = LocalDateTime.now())
                                "49471248" -> statistikkTilDVH.copy(behandlingId = "48339902", tekniskTid = LocalDateTime.now())
                                "49458524" -> statistikkTilDVH.copy(behandlingId = "48500152", tekniskTid = LocalDateTime.now())
                                "49484111" -> statistikkTilDVH.copy(behandlingId = "47195863", tekniskTid = LocalDateTime.now())
                                "49471306" -> statistikkTilDVH.copy(behandlingId = "47216398", tekniskTid = LocalDateTime.now())
                                "49903049" -> statistikkTilDVH.copy(behandlingId = "48353895", tekniskTid = LocalDateTime.now())
                                "49780699" -> statistikkTilDVH.copy(behandlingId = "49074687", tekniskTid = LocalDateTime.now())
                                "49864813" -> statistikkTilDVH.copy(behandlingId = "48287509", tekniskTid = LocalDateTime.now())
                                "49906209" -> statistikkTilDVH.copy(behandlingId = "48329101", tekniskTid = LocalDateTime.now())
                                else -> throw RuntimeException("Unknown behandlingId: ${statistikkTilDVH.behandlingId}")
                            }

                            preparedStatement.setString(1, jacksonObjectMapper().writeValueAsString(modifiedVersion))
                            preparedStatement.setObject(2,"IKKE_SENDT")
                            preparedStatement.setObject(3, kafkaEventId)

                            preparedStatement.executeUpdate()
                        }
                    }
                }
        }
    }
}