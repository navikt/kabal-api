package no.nav.klage.oppgave.util

import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.domain.klage.*
import java.time.LocalDateTime

fun createTildelingHistory(
    tildelingHistorikkSet: Set<TildelingHistorikk>,
    behandlingCreated: LocalDateTime,
): List<WithPrevious<TildelingEvent>> {
    val historySorted = if (tildelingHistorikkSet.size == 1) {
        listOf(
            TildelingHistorikk(
                saksbehandlerident = null,
                enhet = null,
                tidspunkt = behandlingCreated,
                fradelingReason = null,
                utfoerendeIdent = null

            )
        ) + tildelingHistorikkSet.sortedBy { it.tidspunkt }
    } else tildelingHistorikkSet.sortedBy { it.tidspunkt }

    return historySorted.zipWithNext().map { (previous, current) ->
        val previousEvent = HistoryEvent(
            type = HistoryEventType.TILDELING,
            timestamp = previous.tidspunkt,
            actor = previous.utfoerendeIdent,
            event = TildelingEvent(
                saksbehandler = previous.saksbehandlerident,
                fradelingReasonId = previous.fradelingReason?.id,
            ),
        )

        HistoryEventWithPrevious(
            type = HistoryEventType.TILDELING,
            timestamp = current.tidspunkt,
            actor = current.utfoerendeIdent,
            event = TildelingEvent(
                saksbehandler = current.saksbehandlerident,
                fradelingReasonId = current.fradelingReason?.id,
            ),
            previous = previousEvent,
        )
    }

}

fun createMedunderskriverHistory(
    medunderskriverHistorikkSet: Set<MedunderskriverHistorikk>,
    behandlingCreated: LocalDateTime,
): List<WithPrevious<MedunderskriverEvent>> {
    val historySorted = if (medunderskriverHistorikkSet.size == 1) {
        listOf(
            MedunderskriverHistorikk(
                saksbehandlerident = null,
                tidspunkt = behandlingCreated,
                utfoerendeIdent = null,
                flowState = null,
            )
        ) + medunderskriverHistorikkSet.sortedBy { it.tidspunkt }
    } else medunderskriverHistorikkSet.sortedBy { it.tidspunkt }

    return historySorted.zipWithNext()
        .map { (previous, current) ->
            val previousEvent: HistoryEvent<MedunderskriverEvent> = HistoryEvent(
                type = HistoryEventType.MEDUNDERSKRIVER,
                timestamp = previous.tidspunkt,
                actor = previous.utfoerendeIdent,
                event = MedunderskriverEvent(
                    medunderskriver = previous.saksbehandlerident,
                    flow = previous.flowState
                )
            )

            HistoryEventWithPrevious(
                type = HistoryEventType.MEDUNDERSKRIVER,
                timestamp = current.tidspunkt,
                actor = current.utfoerendeIdent,
                event = MedunderskriverEvent(
                    medunderskriver = current.saksbehandlerident,
                    flow = current.flowState
                ),
                previous = previousEvent,
            )
        }

}

fun createRolHistory(
    rolHistorikk: Set<RolHistorikk>,
): List<WithPrevious<RolEvent>> {
    val historySorted = rolHistorikk.sortedBy { it.tidspunkt }

    return historySorted.zipWithNext()
        .map { (previous, current) ->
            val previousEvent: HistoryEvent<RolEvent> = HistoryEvent(
                type = HistoryEventType.ROL,
                timestamp = previous.tidspunkt,
                actor = previous.utfoerendeIdent,
                event = RolEvent(
                    rol = previous.rolIdent,
                    flow = previous.flowState
                )
            )

            HistoryEventWithPrevious(
                type = HistoryEventType.ROL,
                timestamp = current.tidspunkt,
                actor = current.utfoerendeIdent,
                event = RolEvent(
                    rol = current.rolIdent,
                    flow = current.flowState
                ),
                previous = previousEvent,
            )
        }

}

fun createFeilregistrertHistory(
    feilregistrering: Feilregistrering?,
    behandlingCreated: LocalDateTime
): List<WithPrevious<FeilregistrertEvent>> {
    return if (feilregistrering != null) {
        return listOf(
            HistoryEventWithPrevious(
                type = HistoryEventType.FEILREGISTRERT,
                timestamp = feilregistrering.registered,
                actor = feilregistrering.navIdent,
                event = FeilregistrertEvent(
                    reason = feilregistrering.reason,
                ),
                previous = HistoryEvent(
                    type = HistoryEventType.FEILREGISTRERT,
                    timestamp = behandlingCreated,
                    actor = null,
                    event = FeilregistrertEvent(
                        reason = feilregistrering.reason,
                    )
                ),
            )
        )
    } else emptyList()
}

fun createFullmektigHistory(
    fullmektigHistorikk: Set<FullmektigHistorikk>,
): List<WithPrevious<FullmektigEvent>> {
    val historySorted = fullmektigHistorikk.sortedBy { it.tidspunkt }

    return historySorted.zipWithNext()
        .map { (previous, current) ->
            val previousEvent: HistoryEvent<FullmektigEvent> = HistoryEvent(
                type = HistoryEventType.FULLMEKTIG,
                timestamp = previous.tidspunkt,
                actor = previous.utfoerendeIdent,
                event = FullmektigEvent(
                    part = previous.partId?.let {
                        Part(
                            id = it.value,
                            type = if (it.type == PartIdType.PERSON) {
                                BehandlingDetaljerView.IdType.FNR
                            } else BehandlingDetaljerView.IdType.ORGNR
                        )
                    }
                )
            )

            HistoryEventWithPrevious(
                type = HistoryEventType.FULLMEKTIG,
                timestamp = current.tidspunkt,
                actor = current.utfoerendeIdent,
                event = FullmektigEvent(
                    part = current.partId?.let {
                        Part(
                            id = it.value,
                            type = if (it.type == PartIdType.PERSON) {
                                BehandlingDetaljerView.IdType.FNR
                            } else BehandlingDetaljerView.IdType.ORGNR
                        )
                    }
                ),
                previous = previousEvent,
            )
        }
}

fun createKlagerHistory(
    klagerHistorikk: Set<KlagerHistorikk>,
): List<WithPrevious<KlagerEvent>> {
    val historySorted = klagerHistorikk.sortedBy { it.tidspunkt }

    return historySorted.zipWithNext()
        .map { (previous, current) ->
            val previousEvent: HistoryEvent<KlagerEvent> = HistoryEvent(
                type = HistoryEventType.KLAGER,
                timestamp = previous.tidspunkt,
                actor = previous.utfoerendeIdent,
                event = KlagerEvent(
                    part = previous.partId.let {
                        Part(
                            id = it.value,
                            type = if (it.type == PartIdType.PERSON) {
                                BehandlingDetaljerView.IdType.FNR
                            } else BehandlingDetaljerView.IdType.ORGNR
                        )
                    }
                )
            )

            HistoryEventWithPrevious(
                type = HistoryEventType.KLAGER,
                timestamp = current.tidspunkt,
                actor = current.utfoerendeIdent,
                event = KlagerEvent(
                    part = current.partId.let {
                        Part(
                            id = it.value,
                            type = if (it.type == PartIdType.PERSON) {
                                BehandlingDetaljerView.IdType.FNR
                            } else BehandlingDetaljerView.IdType.ORGNR
                        )
                    }
                ),
                previous = previousEvent,
            )
        }
}

fun createSattPaaVentHistory(
    sattPaaVentHistorikk: Set<SattPaaVentHistorikk>,
): List<WithPrevious<SattPaaVentEvent>> {
    return emptyList()
}

fun createFerdigstiltHistory(): List<WithPrevious<BaseEvent<*>>> {
    return emptyList()
}

