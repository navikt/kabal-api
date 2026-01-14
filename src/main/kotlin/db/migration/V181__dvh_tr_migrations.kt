package db.migration

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.domain.kafka.BehandlingState
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.StatistikkTilDVH
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus
import no.nav.klage.oppgave.service.getDVHPart
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*

class V181__dvh_tr_migrations : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val preparedStatement = context.connection.prepareStatement(
            """
                insert into klage.kafka_event (id, behandling_id, kilde, kilde_referanse, status_id, json_payload, type, created, error_message)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        )

        context.connection.createStatement().use { select ->
            select.executeQuery(
                """
                    select b.id,
                     b.dvh_referanse,
                     b.sendt_til_trygderetten,
                     b.saken_gjelder_value,
                     b.saken_gjelder_type,
                     b.klager_value,
                     b.klager_type,
                     b.dato_mottatt_klageinstans,
                     b.tildelt_saksbehandlerident,
                     b.tildelt_enhet,
                     b.ytelse_id,
                     b.sak_fagsak_id,
                     b.kilde_referanse
                    from klage.behandling b
                    where b.sak_fagsystem = '${Fagsystem.PP01.id}'
                    and b.type_id = '${Type.ANKE_I_TRYGDERETTEN.id}'
                    and b.feilregistrering_registered is null
                    and b.previous_saksbehandlerident is null
                    """
            )
                .use { rows ->
                    val now = LocalDateTime.now()
                    while (rows.next()) {
                        val behandlingId = rows.getObject(1, UUID::class.java)
                        val dvhReferanse = rows.getString(2)
                        val sendtTilTR: Timestamp = rows.getTimestamp(3)
                        val sakenGjelder = rows.getString(4)
                        val sakenGjelderType = rows.getString(5)
                        val klager = rows.getString(6)
                        val klagerType = rows.getString(7)
                        val mottattKlageinstans = rows.getDate(8)
                        val saksbehandler = rows.getString(9)
                        val tildeltEnhet = rows.getString(10)
                        val ytelseId = rows.getString(11)
                        val fagsakId = rows.getString(12)
                        val kildeReferanse = rows.getString(13)

                        val eventId = UUID.randomUUID()

                        //Usually we have an Anke when creating this event, so fewer data is available now.

                        val statistikkTilDVH = StatistikkTilDVH(
                            eventId = eventId,
                            behandlingId = dvhReferanse,
                            behandlingIdKabal = behandlingId.toString(),
                            //Means enhetTildeltDato
                            behandlingStartetKA = null,
                            ansvarligEnhetKode = "TR0000",
                            behandlingStatus = BehandlingState.SENDT_TIL_TR,
                            behandlingType = Type.ANKE.name,
                            //Means medunderskriver
                            beslutter = null,
                            endringstid = sendtTilTR.toLocalDateTime(),
                            hjemmel = emptyList(),
                            klager = getDVHPart(PartIdType.valueOf(klagerType), klager),
                            opprinneligFagsaksystem = Fagsystem.PP01.navn,
                            overfoertKA = mottattKlageinstans.toLocalDate(),
                            resultat = null,
                            sakenGjelder = getDVHPart(PartIdType.valueOf(sakenGjelderType), sakenGjelder),
                            saksbehandler = saksbehandler,
                            saksbehandlerEnhet = tildeltEnhet,
                            tekniskTid = now,
                            vedtaksdato = null,
                            ytelseType = Ytelse.of(ytelseId).name,
                            opprinneligFagsakId = fagsakId,
                        )

                        preparedStatement.setObject(1, eventId)
                        preparedStatement.setObject(2, behandlingId)
                        preparedStatement.setString(3, Fagsystem.PP01.navn)
                        preparedStatement.setString(4, kildeReferanse)
                        preparedStatement.setString(5, UtsendingStatus.IKKE_SENDT.name)
                        preparedStatement.setString(6, jacksonObjectMapper().writeValueAsString(statistikkTilDVH))
                        preparedStatement.setString(7, EventType.STATS_DVH.name)
                        preparedStatement.setObject(8, now)
                        preparedStatement.setObject(9, null)

                        preparedStatement.executeUpdate()
                    }

                }
        }
    }
}