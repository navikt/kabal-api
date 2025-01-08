package db.migration

import no.nav.klage.oppgave.domain.kafka.BehandlingEvent
import no.nav.klage.oppgave.domain.kafka.ExternalUtfall
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.time.LocalDateTime
import java.util.*

class V179__pesys_fix_wrong_outcome : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val preparedStatement = context.connection.prepareStatement(
            """
                INSERT INTO klage.kafka_event (id, behandling_id, kilde, kilde_referanse, json_payload, status_id, error_message, type, created)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        )

        context.connection.createStatement().use { select ->
            select.executeQuery(
                """
                    select ke.behandling_id, ke.kilde, ke.kilde_referanse, ke.json_payload, ke.type
                    from klage.kafka_event ke
                    where ke.id = '54f5191c-a0ab-492b-a9a4-3c8fcad96c17'
                    """
            )
                .use { rows ->
                    while (rows.next()) {
                        val behandlingId = rows.getObject(1, UUID::class.java)
                        val kilde = rows.getString(2)
                        val kildeReferanse = rows.getString(3)
                        val jsonPayload = rows.getString(4)
                        val type = rows.getString(5)

                        val behandlingEvent =
                            ourJacksonObjectMapper().readValue(jsonPayload, BehandlingEvent::class.java)

                        val behandlingEventDetaljerAnkebehandlingAvsluttet =
                            behandlingEvent.detaljer.ankebehandlingAvsluttet!!
                        val oldAvsluttet = behandlingEventDetaljerAnkebehandlingAvsluttet.avsluttet

                        val newBehandlingEventDetaljerAnkebehandlingAvsluttet =
                            behandlingEventDetaljerAnkebehandlingAvsluttet.copy(
                                avsluttet = oldAvsluttet.plusMinutes(5),
                                utfall = ExternalUtfall.STADFESTELSE
                            )

                        val newBehandlingEvent =
                            behandlingEvent.copy(
                                eventId = UUID.randomUUID(),
                                detaljer = behandlingEvent.detaljer.copy(ankebehandlingAvsluttet = newBehandlingEventDetaljerAnkebehandlingAvsluttet)
                            )

                        preparedStatement.setObject(1, UUID.randomUUID())
                        preparedStatement.setObject(2, behandlingId)
                        preparedStatement.setObject(3, kilde)
                        preparedStatement.setObject(4, kildeReferanse)
                        preparedStatement.setString(5, ourJacksonObjectMapper().writeValueAsString(newBehandlingEvent))
                        preparedStatement.setObject(6,"IKKE_SENDT")
                        preparedStatement.setObject(7, null)
                        preparedStatement.setObject(8, type)
                        preparedStatement.setObject(9, LocalDateTime.now())

                        preparedStatement.executeUpdate()
                    }
                }
        }
    }
}