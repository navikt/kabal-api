package no.nav.klage.innsyn.api.view

import java.time.LocalDateTime


data class InnsynResponse(
    val saker: List<SakView>,
//    val active: List<SakView>,
//    val finished: List<SakView>,
)

data class SakView(
    val id: String, //created using fagsystemId and saksnummer
    val saksnummer: String,
    val ytelseId: String,
    val innsendingsytelseId: String?,
    val events: List<Event>,
) {
    data class Event(
        val type: EventType,
        val date: LocalDateTime,
    ) {
        enum class EventType {
            KLAGE_MOTTATT_VEDTAKSINSTANS,
            KLAGE_MOTTATT_KLAGEINSTANS,
            KLAGE_AVSLUTTET_I_KLAGEINSTANS,
            ANKE_MOTTATT_KLAGEINSTANS,
            ANKE_SENDT_TRYGDERETTEN,
            ANKE_KJENNELSE_MOTTATT_FRA_TRYGDERETTEN,
            ANKE_AVSLUTTET_I_TRYGDERETTEN,
            ANKE_AVSLUTTET_I_KLAGEINSTANS,
        }
    }
}