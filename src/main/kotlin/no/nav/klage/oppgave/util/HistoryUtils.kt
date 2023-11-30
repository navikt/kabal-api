package no.nav.klage.oppgave.util

import no.nav.klage.kodeverk.FlowState
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.domain.klage.*

fun createTildelingHistory(
    tildelingHistorikkSet: Set<TildelingHistorikk>,
): List<WithPrevious<TildelingEvent>> {
    if (tildelingHistorikkSet.isEmpty()) {
        return emptyList()
    }

    fun getType(tildelingHistorikk: TildelingHistorikk): HistoryEventType =
        if (tildelingHistorikk.saksbehandlerident == null) {
            HistoryEventType.FRADELT
        } else {
            HistoryEventType.TILDELT
        }

    var previousEvent: HistoryEvent<TildelingEvent>? = null

    return tildelingHistorikkSet.sortedBy { it.tidspunkt }.mapIndexed { index, tildelingHistorikk ->
        val currentEvent = HistoryEvent(
            type = if (index == 0) HistoryEventType.TILDELT_INITIAL else getType(tildelingHistorikk),
            timestamp = tildelingHistorikk.tidspunkt,
            actor = tildelingHistorikk.utfoerendeIdent,
            event = TildelingEvent(
                saksbehandler = tildelingHistorikk.saksbehandlerident,
                fradelingReasonId = tildelingHistorikk.fradelingReason?.id,
            ),
        )

        HistoryEventWithPrevious(
            type = currentEvent.type,
            timestamp = currentEvent.timestamp,
            actor = currentEvent.actor,
            event = currentEvent.event,
            previous = previousEvent,
        ).also {
            previousEvent = currentEvent
        }
    }
}

fun createMedunderskriverHistory(
    medunderskriverHistorikkSet: Set<MedunderskriverHistorikk>,
): List<WithPrevious<MedunderskriverEvent>> {

    fun getType(current: MedunderskriverHistorikk, previous: MedunderskriverHistorikk?): HistoryEventType {
        if (previous == null) {
            return HistoryEventType.SET_MEDUNDERSKRIVER_INITIAL
        }

        if (previous.saksbehandlerident == null && current.saksbehandlerident != null) {
            return HistoryEventType.SET_MEDUNDERSKRIVER
        }

        if (previous.saksbehandlerident != null && current.saksbehandlerident == null) {
            return HistoryEventType.SET_MEDUNDERSKRIVER //?
        }

        if (previous.flowState != current.flowState && current.flowState == FlowState.SENT) {
            return HistoryEventType.SENT_TO_MEDUNDERSKRIVER
        }

        if (previous.flowState != current.flowState && current.flowState == FlowState.RETURNED) {
            return HistoryEventType.RETURNED_FROM_MEDUNDERSKRIVER
        }

        if (previous.flowState != current.flowState && current.flowState == FlowState.NOT_SENT) {
            return HistoryEventType.RETRACTED_FROM_MEDUNDERSKRIVER
        }

        error("what?")
    }

    var previousEvent: HistoryEvent<MedunderskriverEvent>? = null

    val medunderskriverHistorikkSorted = medunderskriverHistorikkSet.sortedBy { it.tidspunkt }

    return medunderskriverHistorikkSorted.mapIndexed { index, medunderskriverHistorikk ->
        val currentEvent: HistoryEvent<MedunderskriverEvent> = HistoryEvent(
            type = getType(current = medunderskriverHistorikk, previous = if (index > 0) medunderskriverHistorikkSorted[index - 1] else null),
            timestamp = medunderskriverHistorikk.tidspunkt,
            actor = medunderskriverHistorikk.utfoerendeIdent,
            event = MedunderskriverEvent(
                medunderskriver = medunderskriverHistorikk.saksbehandlerident ?: "",
                flow = medunderskriverHistorikk.flowState
            )
        )

        HistoryEventWithPrevious(
            type = currentEvent.type,
            timestamp = currentEvent.timestamp,
            actor = currentEvent.actor,
            event = currentEvent.event,
            previous = previousEvent
        ).also {
            previousEvent = currentEvent
        }
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

fun createRolHistory(
    rolHistorikk: Set<RolHistorikk>,
): List<WithPrevious<RolEvent>> {
    return emptyList()
}