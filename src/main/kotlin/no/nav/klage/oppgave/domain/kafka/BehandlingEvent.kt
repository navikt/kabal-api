package no.nav.klage.oppgave.domain.kafka

import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.klage.BehandlingEtterTrygderettenOpphevet
import java.time.LocalDateTime
import java.util.*

data class BehandlingEvent(
    val eventId: UUID,
    val kildeReferanse: String,
    val kilde: String,
    val kabalReferanse: String,
    val type: BehandlingEventType,
    val detaljer: BehandlingDetaljer,
)

enum class BehandlingEventType {
    KLAGEBEHANDLING_AVSLUTTET,
    ANKEBEHANDLING_OPPRETTET,
    ANKEBEHANDLING_AVSLUTTET,
    ANKE_I_TRYGDERETTENBEHANDLING_OPPRETTET,
    BEHANDLING_FEILREGISTRERT,
    BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET_AVSLUTTET,
}

data class BehandlingDetaljer(
    val klagebehandlingAvsluttet: KlagebehandlingAvsluttetDetaljer? = null,
    val ankebehandlingOpprettet: AnkebehandlingOpprettetDetaljer? = null,
    val ankebehandlingAvsluttet: AnkebehandlingAvsluttetDetaljer? = null,
    val ankeITrygderettenbehandlingOpprettet: AnkeITrygderettenbehandlingOpprettetDetaljer? = null,
    val behandlingFeilregistrert: BehandlingFeilregistrertDetaljer? = null,
    val behandlingEtterTrygderettenOpphevetAvsluttet: BehandlingEtterTrygderettenOpphevetAvsluttetDetaljer? = null
)

data class KlagebehandlingAvsluttetDetaljer(
    val avsluttet: LocalDateTime,
    val utfall: ExternalUtfall,
    val journalpostReferanser: List<String>,
)


data class AnkebehandlingOpprettetDetaljer(
    val mottattKlageinstans: LocalDateTime
)

data class AnkeITrygderettenbehandlingOpprettetDetaljer(
    val sendtTilTrygderetten: LocalDateTime,
    val utfall: ExternalUtfall?,
)

data class AnkebehandlingAvsluttetDetaljer(
    val avsluttet: LocalDateTime,
    val utfall: ExternalUtfall,
    val journalpostReferanser: List<String>,
)

data class BehandlingEtterTrygderettenOpphevetAvsluttetDetaljer(
    val avsluttet: LocalDateTime,
    val utfall: ExternalUtfall,
    val journalpostReferanser: List<String>,
)

data class BehandlingFeilregistrertDetaljer(
    val feilregistrert: LocalDateTime,
    val navIdent: String,
    val reason: String,
    val type: Type,
)
