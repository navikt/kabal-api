package no.nav.klage.innsyn.api.view

import java.time.LocalDate
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
    val events: List<Event>,
    val varsletBehandlingstid: VarsletBehandlingstid?,
    val mottattKlageinstans: LocalDate,
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
            ANKE_AVSLUTTET_I_TRYGDERETTEN, //Do we need this, when we have ANKE_KJENNELSE_MOTTATT_FRA_TRYGDERETTEN?
            ANKE_AVSLUTTET_I_KLAGEINSTANS,
        }
    }

    data class VarsletBehandlingstid(
        val varsletBehandlingstidUnits: Int,
        val varsletBehandlingstidUnitTypeId: String,
        val varsletFrist: LocalDate,
    )
}