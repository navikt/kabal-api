package db.migration

import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.time.LocalDateTime
import java.util.*

class V187__dvh_pesys_fix_migrated_anker_i_tr : BaseJavaMigration() {
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
'42732182',
'50839922',
'51000174',
'51237578',
'54868291',
'56015005',
'58105978',
'58123865',
'58164030',
'58242017',
'59562571',
'59570942',
'59677898',
'59858460',
'61798791',
'62711221',
'62762092',
'62770112',
'62771621',
'63239959',
'63252563',
'65002130',
'65025720',
'65043518',
'65079148',
'65092341',
'65110126',
'65118462',
'65229724',
'65515231',
'65515785',
'65519983',
'65665971',
'65677516',
'65677635',
'65677689',
'65694656',
'65697538',
'65881836',
'66015487',
'66037964',
'66056048',
'66074680',
'66079224',
'66080169',
'66089585',
'66089958',
'66097917',
'66105405',
'66141305',
'66218596',
'66235147',
'68380341',
'68400811'
)
                    """
            )
                .use { rows ->
                    while (rows.next()) {
                        val kafkaEventId = rows.getObject(1, UUID::class.java)
                        val jsonPayload = rows.getString(2)

                        val statistikkTilDVH =
                            ourJacksonObjectMapper().readValue(jsonPayload, StatistikkTilDVH::class.java)

                        val modifiedVersion = when (statistikkTilDVH.behandlingId) {
                            "29929939" -> statistikkTilDVH.copy(behandlingId = "46924798", tekniskTid = LocalDateTime.now())
                            "34591160" -> statistikkTilDVH.copy(behandlingId = "37553958", tekniskTid = LocalDateTime.now())
                            "35971378" -> statistikkTilDVH.copy(behandlingId = "37042716", tekniskTid = LocalDateTime.now())
                            "36204480" -> statistikkTilDVH.copy(behandlingId = "44906368", tekniskTid = LocalDateTime.now())
                            "36297746" -> statistikkTilDVH.copy(behandlingId = "39240108", tekniskTid = LocalDateTime.now())
                            "36916837" -> statistikkTilDVH.copy(behandlingId = "46731299", tekniskTid = LocalDateTime.now())
                            "39430889" -> statistikkTilDVH.copy(behandlingId = "40490013", tekniskTid = LocalDateTime.now())
                            "39569750" -> statistikkTilDVH.copy(behandlingId = "41056652", tekniskTid = LocalDateTime.now())
                            "39290541" -> statistikkTilDVH.copy(behandlingId = "42409863", tekniskTid = LocalDateTime.now())
                            "39419643" -> statistikkTilDVH.copy(behandlingId = "48464095", tekniskTid = LocalDateTime.now())
                            "40296601" -> statistikkTilDVH.copy(behandlingId = "43360766", tekniskTid = LocalDateTime.now())
                            "40394607" -> statistikkTilDVH.copy(behandlingId = "44368650", tekniskTid = LocalDateTime.now())
                            "42524508" -> statistikkTilDVH.copy(behandlingId = "46795526", tekniskTid = LocalDateTime.now())
                            "42763873" -> statistikkTilDVH.copy(behandlingId = "48372473", tekniskTid = LocalDateTime.now())
                            "43016284" -> statistikkTilDVH.copy(behandlingId = "48386800", tekniskTid = LocalDateTime.now())
                            "45180705" -> statistikkTilDVH.copy(behandlingId = "47032750", tekniskTid = LocalDateTime.now())
                            "44771630" -> statistikkTilDVH.copy(behandlingId = "50200505", tekniskTid = LocalDateTime.now())
                            "44874762" -> statistikkTilDVH.copy(behandlingId = "49229515", tekniskTid = LocalDateTime.now())
                            "45698696" -> statistikkTilDVH.copy(behandlingId = "48803016", tekniskTid = LocalDateTime.now())
                            "44511107" -> statistikkTilDVH.copy(behandlingId = "46705285", tekniskTid = LocalDateTime.now())
                            "45155349" -> statistikkTilDVH.copy(behandlingId = "48211098", tekniskTid = LocalDateTime.now())
                            "46685908" -> statistikkTilDVH.copy(behandlingId = "48273054", tekniskTid = LocalDateTime.now())
                            "46592319" -> statistikkTilDVH.copy(behandlingId = "49153114", tekniskTid = LocalDateTime.now())
                            "46863905" -> statistikkTilDVH.copy(behandlingId = "47165156", tekniskTid = LocalDateTime.now())
                            "46761693" -> statistikkTilDVH.copy(behandlingId = "47789603", tekniskTid = LocalDateTime.now())
                            "46581200" -> statistikkTilDVH.copy(behandlingId = "48279856", tekniskTid = LocalDateTime.now())
                            "46796956" -> statistikkTilDVH.copy(behandlingId = "48632600", tekniskTid = LocalDateTime.now())
                            "46966952" -> statistikkTilDVH.copy(behandlingId = "48360549", tekniskTid = LocalDateTime.now())
                            "46871851" -> statistikkTilDVH.copy(behandlingId = "49775165", tekniskTid = LocalDateTime.now())
                            "46987163" -> statistikkTilDVH.copy(behandlingId = "48655923", tekniskTid = LocalDateTime.now())
                            "46831512" -> statistikkTilDVH.copy(behandlingId = "49189245", tekniskTid = LocalDateTime.now())
                            "47050347" -> statistikkTilDVH.copy(behandlingId = "48610584", tekniskTid = LocalDateTime.now())
                            "47112614" -> statistikkTilDVH.copy(behandlingId = "48656851", tekniskTid = LocalDateTime.now())
                            "47221565" -> statistikkTilDVH.copy(behandlingId = "48830671", tekniskTid = LocalDateTime.now())
                            "47223409" -> statistikkTilDVH.copy(behandlingId = "49064507", tekniskTid = LocalDateTime.now())
                            "47232933" -> statistikkTilDVH.copy(behandlingId = "48815445", tekniskTid = LocalDateTime.now())
                            "47195122" -> statistikkTilDVH.copy(behandlingId = "49083804", tekniskTid = LocalDateTime.now())
                            "47676204" -> statistikkTilDVH.copy(behandlingId = "49088281", tekniskTid = LocalDateTime.now())
                            "47075843" -> statistikkTilDVH.copy(behandlingId = "48496134", tekniskTid = LocalDateTime.now())
                            "47789246" -> statistikkTilDVH.copy(behandlingId = "49120882", tekniskTid = LocalDateTime.now())
                            "47677558" -> statistikkTilDVH.copy(behandlingId = "48656611", tekniskTid = LocalDateTime.now())
                            "48191344" -> statistikkTilDVH.copy(behandlingId = "49081011", tekniskTid = LocalDateTime.now())
                            "48226548" -> statistikkTilDVH.copy(behandlingId = "48376197", tekniskTid = LocalDateTime.now())
                            "48235012" -> statistikkTilDVH.copy(behandlingId = "49420080", tekniskTid = LocalDateTime.now())
                            "48230503" -> statistikkTilDVH.copy(behandlingId = "49128417", tekniskTid = LocalDateTime.now())
                            "48253006" -> statistikkTilDVH.copy(behandlingId = "49091125", tekniskTid = LocalDateTime.now())
                            "48020994" -> statistikkTilDVH.copy(behandlingId = "48816117", tekniskTid = LocalDateTime.now())
                            "46852801" -> statistikkTilDVH.copy(behandlingId = "49088709", tekniskTid = LocalDateTime.now())
                            "47794890" -> statistikkTilDVH.copy(behandlingId = "49120897", tekniskTid = LocalDateTime.now())
                            "48348749" -> statistikkTilDVH.copy(behandlingId = "49182562", tekniskTid = LocalDateTime.now())
                            "48492846" -> statistikkTilDVH.copy(behandlingId = "49219505", tekniskTid = LocalDateTime.now())
                            "48412294" -> statistikkTilDVH.copy(behandlingId = "49054828", tekniskTid = LocalDateTime.now())
                            "48396802" -> statistikkTilDVH.copy(behandlingId = "49189586", tekniskTid = LocalDateTime.now())
                            "49190951" -> statistikkTilDVH.copy(behandlingId = "49420081", tekniskTid = LocalDateTime.now())


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