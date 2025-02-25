package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*

class V196__record_varslet_behandlingstid_history : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val preparedStatement = context.connection.prepareStatement(
            """
                insert into klage.varslet_behandlingstid_historikk (id, behandling_id, tidspunkt, utfoerende_ident, utfoerende_navn, varslet_frist, varslet_behandlingstid_units, varslet_behandlingstid_unit_type_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        )

        context.connection.createStatement().use { select ->
            select.executeQuery(
                """
                    select b.id,
                     b.created,
                     b.varslet_frist,
                     b.varslet_behandlingstid_units,
                     b.varslet_behandlingstid_unit_type_id
                    from klage.behandling b
                    where b.id IN (
                        'b88ec117-b2de-4310-8c01-cc79d93616f6',
                        '663fb07c-930e-44fc-8099-e06be0080171',
                        '68626949-fd2d-43b5-ab0f-774975bdf74a',
                        '403156ef-99a8-4148-8f72-caa059d712e3',
                        'c93ddec7-5f9e-4c8d-9b97-4deeac53ecdf',
                        '3955fd7f-c67e-4737-abf7-363f662beba4',
                        'd6543a06-d2c3-4812-b8cb-cfe16d813937',
                        'a79a3194-66fe-4c96-ab66-aa5f48cb7379',
                        '49c32eb7-8041-46df-9f76-1034329081f5',
                        '7e9b6b77-45ea-4088-b457-9a0a29d30e27',
                        '6ec424b2-da9e-4dfa-a060-e6f213317fdb',
                        '3c457500-7385-47fb-94f4-17bc075a2731',
                        '6febecf0-cea4-4efe-90b4-6231e3255e3c',
                        '64d4eb76-b047-4526-a8ab-579ca8ab932f',
                        '30fe82c0-5ff3-4608-a417-175a5128e648',
                        'b913820f-43c3-4970-9daf-4cfbcc923c0d',
                        'b3da3768-00f8-4449-ad3b-1cc5054ebceb',
                        '4c57c702-5802-4df0-bcae-6e11bc257195',
                        '8e3db198-2d09-448b-af95-dc949160b78a',
                        '47bd61b0-2a91-4c54-b959-fc49646d521a',
                        '11191445-cb0b-40ec-9421-5039e96c5e1e',
                        '41f39590-256f-4daf-8ed2-11708bc47d65',
                        '85d6c5e3-da27-4141-ba90-6de19ab52a71',
                        '92189a6e-afe4-482d-b137-661f860a2f20',
                        'dd574c52-ddcb-4d82-8d92-7d18c59eb801',
                        '8c7e3fac-287f-4346-b24d-824e1b5c991a',
                        'f9fb0294-a30a-4d70-a078-21825c659175',
                        '2efe28b8-4282-47f7-b9c2-4a7fcf9d7fa7',
                        '45fd6f99-7e7d-4b7c-bc97-80aaf62dcc37',
                        'c970b876-22f5-4ada-b3b0-a6f30e839319',
                        '3669bca7-f71d-47cb-8d34-bb27cdd3ce63',
                        '00e48c64-bc2e-44fd-b1e8-589957f1da1a',
                        '7ac59784-c955-45c1-abd4-7c2da8bdee12',
                        '7748f7b9-1f4f-4bf8-a8da-7c1daa7ccd6f',
                        'a02f2f96-9d13-4f08-bb5b-f6a30a73d4ad',
                        '29334dd2-dbd9-44a2-813c-a56f5a900bdb'
    )                     
                    """
            )
                .use { rows ->
                    while (rows.next()) {
                        val behandlingId = rows.getObject(1, UUID::class.java)
                        val created: Timestamp = rows.getTimestamp(2)
                        val varsletFrist = rows.getDate(3)
                        val varsletBehandlingstidUnits = rows.getInt(4)
                        val varsletBehandlingstidTypeId = rows.getString(5)

                        //Null case
                        preparedStatement.setObject(1, UUID.randomUUID())
                        preparedStatement.setObject(2, behandlingId)
                        preparedStatement.setObject(3, created)
                        preparedStatement.setString(4, null)
                        preparedStatement.setString(5, null)
                        preparedStatement.setString(6, null)
                        preparedStatement.setString(7, null)
                        preparedStatement.setString(8, null)

                        preparedStatement.executeUpdate()

                        //First entry
                        preparedStatement.setObject(1, UUID.randomUUID())
                        preparedStatement.setObject(2, behandlingId)
                        preparedStatement.setObject(3, LocalDateTime.now())
                        preparedStatement.setString(4, "B126820")
                        preparedStatement.setString(5, "Kristin Åsene Buskerud")
                        preparedStatement.setObject(6, varsletFrist)
                        preparedStatement.setInt(7, varsletBehandlingstidUnits)
                        preparedStatement.setString(8, varsletBehandlingstidTypeId)

                        preparedStatement.executeUpdate()
                    }
                }
        }
    }
}