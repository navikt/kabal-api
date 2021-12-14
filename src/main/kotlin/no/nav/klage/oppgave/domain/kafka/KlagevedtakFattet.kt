package no.nav.klage.oppgave.domain.kafka

import java.util.*

/**
 * Brukes for å sende info om vedtak fattet til 1. instans
 */
data class KlagevedtakFattet(
    /** Kan brukes til idempotency av konsumenter */
    val eventId: UUID,
    val kildeReferanse: String,
    val kilde: String,
    val utfall: ExternalUtfall,
    val vedtaksbrevReferanse: String?,
    /** Per i dag, vedtak_id */
    val kabalReferanse: String
)
