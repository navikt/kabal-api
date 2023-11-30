package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.FlowState
import java.time.LocalDate
import java.time.LocalDateTime

data class TildelingEvent(
    val saksbehandler: String?,
    val fradelingReasonId: String?,
)

data class MedunderskriverEvent(
    val medunderskriver: String?,
    val flow: FlowState?
)

data class RolEvent(
    val rol: String,
    val flow: FlowState
)

data class SattPaaVentEvent(
    val from: LocalDate,
    val to: LocalDate,
    val reason: String
)

data class FeilregistrertEvent(
    val reason: String
)

data class KlagerEvent(
    val part: Part,
) {
    data class Part(
        val id: String,
        val type: BehandlingDetaljerView.IdType
    )
}

interface WithPrevious<T>: BaseEvent<T> {
    val previous: BaseEvent<T>
}

data class HistoryEventWithPrevious<T>(
    override val type: HistoryEventType,
    override val timestamp: LocalDateTime,
    override val actor: String?,
    override val event: T?,
    override val previous: BaseEvent<T>
): WithPrevious<T>

interface BaseEvent<T> {
    val type: HistoryEventType
    val timestamp: LocalDateTime
    val actor: String?
    val event: T?
}

data class HistoryEvent<T>(
    override val type: HistoryEventType,
    override val timestamp: LocalDateTime,
    override val actor: String?,
    override val event: T?
): BaseEvent<T>

data class HistoryResponse(
    val tildeling: List<WithPrevious<TildelingEvent>>,
    val medunderskriver: List<WithPrevious<MedunderskriverEvent>>,
    val rol: List<WithPrevious<RolEvent>>,
    val klager: List<WithPrevious<KlagerEvent>>,
    val sattPaaVent: List<WithPrevious<SattPaaVentEvent>>,
    val ferdigstilt: List<WithPrevious<BaseEvent<*>>>,
    val feilregistrert: List<WithPrevious<FeilregistrertEvent>>
)

enum class HistoryEventType {
    TILDELING,
    MEDUNDERSKRIVER,
    ROL,
    KLAGER,
    SATT_PAA_VENT,
    FERDIGSTILT,
    FEILREGISTRERT,
}