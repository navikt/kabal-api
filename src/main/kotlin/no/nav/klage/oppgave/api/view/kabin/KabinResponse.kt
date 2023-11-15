package no.nav.klage.oppgave.api.view.kabin

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.api.view.DokumentReferanse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class CompletedBehandling(
    val behandlingId: UUID,
    val ytelseId: String,
    val utfallId: String,
    val hjemmelId: String,
    val vedtakDate: LocalDateTime,
    val sakenGjelder: BehandlingDetaljerView.SakenGjelderView,
    val klager: BehandlingDetaljerView.PartView,
    val fullmektig: BehandlingDetaljerView.PartView?,
    val fagsakId: String,
    val fagsystem: Fagsystem,
    val fagsystemId: String,
    val klageBehandlendeEnhet: String,
    val tildeltSaksbehandlerIdent: String?,
    val tildeltSaksbehandlerNavn: String?,
)

data class Ankemulighet(
    val behandlingId: UUID,
    val typeId: String,
    val sourceOfExistingAnkebehandling: List<ExistingAnkebehandling>,
    val ytelseId: String,
    val utfallId: String,
    val hjemmelId: String,
    val vedtakDate: LocalDateTime,
    val sakenGjelder: BehandlingDetaljerView.SakenGjelderView,
    val klager: BehandlingDetaljerView.PartView,
    val fullmektig: BehandlingDetaljerView.PartView?,
    val fagsakId: String,
    val fagsystem: Fagsystem,
    val fagsystemId: String,
    val klageBehandlendeEnhet: String,
    val tildeltSaksbehandlerIdent: String?,
    val tildeltSaksbehandlerNavn: String?,
)

data class ExistingAnkebehandling(
    val id: UUID,
    val created: LocalDateTime,
    val completed: LocalDateTime?,
)

data class CreatedAnkeResponse(
    val mottakId: UUID,
)

data class CreatedKlageResponse(
    val mottakId: UUID,
)

data class CreatedAnkebehandlingStatusForKabin(
    val typeId: String,
    val ytelseId: String,
    val utfallId: String,
    val vedtakDate: LocalDateTime,
    val sakenGjelder: BehandlingDetaljerView.SakenGjelderView,
    val klager: BehandlingDetaljerView.PartView,
    val fullmektig: BehandlingDetaljerView.PartView?,
    val mottattNav: LocalDate,
    val frist: LocalDate,
    val fagsakId: String,
    val fagsystemId: String,
    val journalpost: DokumentReferanse,
    val tildeltSaksbehandler: TildeltSaksbehandler?,
)

data class CreatedKlagebehandlingStatusForKabin(
    val typeId: String,
    val ytelseId: String,
    val sakenGjelder: BehandlingDetaljerView.SakenGjelderView,
    val klager: BehandlingDetaljerView.PartView,
    val fullmektig: BehandlingDetaljerView.PartView?,
    val mottattVedtaksinstans: LocalDate,
    val mottattKlageinstans: LocalDate,
    val frist: LocalDate,
    val fagsakId: String,
    val fagsystemId: String,
    val journalpost: DokumentReferanse,
    val kildereferanse: String,
    val tildeltSaksbehandler: TildeltSaksbehandler?,
)

data class TildeltSaksbehandler(
    val navIdent: String,
    val navn: String,
)