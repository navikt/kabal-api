package no.nav.klage.innsyn.api.view

import java.time.LocalDateTime


data class InnsynResponse(
    val active: List<SakView>,
    val finished: List<SakView>,
)

data class SakView(
    val id: String, //consists of typeId, fagsystemId, saksnummer
    val saksnummer: String,
    val typeId: String,
    val ytelseId: String,
    val innsendingsytelseId: String,
    val events: List<Event>,
) {
    data class Event(
        val type: EventType,
        val date: LocalDateTime,
    ) {
        enum class EventType {
            MOTTATT_VEDTAKSINSTANS,
            MOTTATT_KA,
            FERDIG_KA,
            SENDT_TR,
        }
    }
}