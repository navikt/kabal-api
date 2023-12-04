package no.nav.klage.oppgave.util

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

fun createFeilregistrertHistory(): List<WithPrevious<FeilregistrertEvent>> {
    return emptyList()
}

fun createFerdigstiltHistory(): List<WithPrevious<BaseEvent<*>>> {
    return emptyList()
}

fun createSattPaaVentHistory(
    sattPaaVentHistorikk: Set<SattPaaVentHistorikk>,
): List<WithPrevious<SattPaaVentEvent>> {
    return emptyList()
}

fun createKlagerHistory(
    klagerHistorikk: Set<KlagerHistorikk>,
): List<WithPrevious<KlagerEvent>> {
    return emptyList()
}

