package db.migration

import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.oppgave.util.getLogger
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.time.LocalDate
import java.util.*

class V184__fix_varslet_frist : BaseJavaMigration() {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        const val MONTHS = "MONTHS"
        const val WEEKS = "WEEKS"
        const val VARSLET_FRIST = "VARSLET_FRIST"
        const val VARSLET_BEHANDLINGSTID_UNITS = "VARSLET_BEHANDLINGSTID_UNITS"
        const val VARSLET_BEHANDLINGSTID_UNIT_TYPE = "VARSLET_BEHANDLINGSTID_UNIT_TYPE"
    }

    override fun migrate(context: Context) {
        val preparedStatementForUpdate = context.connection.prepareStatement(
            """
                update klage.behandling
                    set varslet_frist = ?, varslet_behandlingstid_unit_type_id = ?, varslet_behandlingstid_units = ?
                    where id = ?
            """.trimIndent()
        )

        val preparedStatementForSelect = context.connection.prepareStatement(
            """
                select felt, tilverdi from klage.endringslogginnslag
                    where felt in ('$VARSLET_FRIST', '$VARSLET_BEHANDLINGSTID_UNITS', '$VARSLET_BEHANDLINGSTID_UNIT_TYPE')
                    and behandling_id = ?
            """.trimIndent()
        )

        context.connection.createStatement().use { select ->
            select.executeQuery(
                """
                    select b.id from klage.mottak m, klage.behandling b
                    where b.mottak_id = m.id
                      and m.type_id = '1'
                      and m.sent_from = 'FAGSYSTEM'
                      and m.created > '2024-11-06'
                      and b.varslet_frist is null
                    """
            )
                .use { rows ->
                    while (rows.next()) {
                        val behandlingId = rows.getObject(1, UUID::class.java)

                        preparedStatementForSelect.setObject(1, behandlingId)

                        val resultSet = preparedStatementForSelect.executeQuery()

                        val varsletMap = mutableMapOf<String, String>()

                        while (resultSet.next()) {
                            varsletMap[resultSet.getString(1)] = resultSet.getString(2)
                        }

                        //check that there are exactly 3 rows in the resultSet and read the values
                        if (
                            varsletMap.size != 3 ||
                            !varsletMap.containsKey(VARSLET_FRIST) ||
                            !varsletMap.containsKey(VARSLET_BEHANDLINGSTID_UNITS) ||
                            !varsletMap.containsKey(VARSLET_BEHANDLINGSTID_UNIT_TYPE)
                        ) {
                            logger.warn("V184__fix_varslet_frist: Missing (or extra) values for behandlingId: $behandlingId. Continuing.")
                            continue
                        }

                        /*
                        Example values:

                        VARSLET_FRIST
                        VARSLET_BEHANDLINGSTID_UNITS
                        VARSLET_BEHANDLINGSTID_UNIT_TYPE

                        2025-06-06
                        4
                        "Type(id=2, navn=MONTHS)"
                         */

                        val varsletFrist = LocalDate.parse(varsletMap[VARSLET_FRIST]!!)
                        val varsletBehandlingstidUnits = varsletMap[VARSLET_BEHANDLINGSTID_UNITS]!!.toInt()

                        val varsletBehandlingstidUnitType =
                            if (varsletMap[VARSLET_BEHANDLINGSTID_UNIT_TYPE]!!.contains(MONTHS)) {
                                TimeUnitType.MONTHS.id
                            } else if (varsletMap[VARSLET_BEHANDLINGSTID_UNIT_TYPE]!!.contains(WEEKS)) {
                                TimeUnitType.WEEKS.id
                            } else {
                                logger.warn("V184__fix_varslet_frist: Unknown TimeUnitType: ${varsletMap[VARSLET_BEHANDLINGSTID_UNIT_TYPE]}. BehandlingId: $behandlingId. Continuing.")
                                continue
                            }

                        preparedStatementForUpdate.setObject(1, varsletFrist)
                        preparedStatementForUpdate.setString(2, varsletBehandlingstidUnitType)
                        preparedStatementForUpdate.setInt(3, varsletBehandlingstidUnits)
                        preparedStatementForUpdate.setObject(4, behandlingId)

                        preparedStatementForUpdate.executeUpdate()
                    }

                }
        }
    }
}