package no.nav.klage.oppgave.domain.kafka

enum class EventType {
    KLAGE_VEDTAK,
    STATS_DVH,
    STATS_DVH_V2,
    BEHANDLING_EVENT,
    MINSIDE_MICROFRONTEND_EVENT,
}
