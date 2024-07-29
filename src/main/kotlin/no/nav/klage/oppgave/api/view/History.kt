package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.FlowState
import java.time.LocalDate
import java.time.LocalDateTime

data class TildelingEvent(
    val saksbehandler: SaksbehandlerView?,
    val fradelingReasonId: String?,
    val hjemmelIdList: List<String>?,
)

data class VarsletBehandlingstidEvent(
    val mottakere: List<Part>?,
    val varsletBehandlingstidUnits: Int?,
    val varsletBehandlingstidUnitTypeId: String?,
    val varsletFrist: LocalDate?,
)

data class MedunderskriverEvent(
    val medunderskriver: SaksbehandlerView?,
    //nullable b/c possible missing history initially
    val flow: FlowState?
)

data class RolEvent(
    val rol: SaksbehandlerView?,
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
)

data class FullmektigEvent(
    val part: Part?,
)

data class FerdigstiltEvent(
    val avsluttetAvSaksbehandler: LocalDateTime,
)

data class Part(
    val id: String,
    val name: String,
    val type: BehandlingDetaljerView.IdType
)

interface WithPrevious<T>: BaseEvent<T> {
    val previous: BaseEvent<T>
}

data class HistoryEventWithPrevious<T>(
    override val type: HistoryEventType,
    override val timestamp: LocalDateTime,
    override val actor: SaksbehandlerView?,
    override val event: T?,
    override val previous: BaseEvent<T>
): WithPrevious<T>

interface BaseEvent<T> {
    val type: HistoryEventType
    val timestamp: LocalDateTime
    val actor: SaksbehandlerView?
    val event: T?
}

data class HistoryEvent<T>(
    override val type: HistoryEventType,
    override val timestamp: LocalDateTime,
    override val actor: SaksbehandlerView?,
    override val event: T?
): BaseEvent<T>

data class HistoryResponse(
    val tildeling: List<WithPrevious<TildelingEvent>>,
    val medunderskriver: List<WithPrevious<MedunderskriverEvent>>,
    val rol: List<WithPrevious<RolEvent>>,
    val klager: List<WithPrevious<KlagerEvent>>,
    val fullmektig: List<WithPrevious<FullmektigEvent>>,
    val sattPaaVent: List<WithPrevious<SattPaaVentEvent>>,
    val ferdigstilt: List<WithPrevious<FerdigstiltEvent>>,
    val feilregistrert: List<WithPrevious<FeilregistrertEvent>>,
    val varsletBehandlingstid: List<WithPrevious<VarsletBehandlingstidEvent>>,
)

enum class HistoryEventType {
    TILDELING,
    MEDUNDERSKRIVER,
    ROL,
    KLAGER,
    FULLMEKTIG,
    SATT_PAA_VENT,
    FERDIGSTILT,
    FEILREGISTRERT,
    VARSLET_BEHANDLINGSTID
}