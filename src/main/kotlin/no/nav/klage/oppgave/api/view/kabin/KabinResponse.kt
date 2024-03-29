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
    val hjemmelIdList: List<String>,
    val vedtakDate: LocalDateTime,
    val sakenGjelder: KabinPartView,
    val klager: KabinPartView,
    val fullmektig: KabinPartView?,
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
    val hjemmelIdList: List<String>,
    val vedtakDate: LocalDateTime,
    val sakenGjelder: KabinPartView,
    val klager: KabinPartView,
    val fullmektig: KabinPartView?,
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
    val vedtakDate: LocalDateTime,
    val sakenGjelder: KabinPartView,
    val klager: KabinPartView,
    val fullmektig: KabinPartView?,
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
    val sakenGjelder: KabinPartView,
    val klager: KabinPartView,
    val fullmektig: KabinPartView?,
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

data class KabinPartView(
    val id: String,
    val type: BehandlingDetaljerView.IdType,
    val name: String,
    val available: Boolean,
    val statusList: List<BehandlingDetaljerView.PartStatus>,
)

fun BehandlingDetaljerView.SakenGjelderView.toKabinPartView(): KabinPartView {
    return KabinPartView(
        id = id,
        type = type,
        name = name,
        available = available,
        statusList = statusList,
    )
}

fun BehandlingDetaljerView.PartView.toKabinPartView(): KabinPartView {
    return KabinPartView(
        id = id,
        type = type,
        name = name,
        available = available,
        statusList = statusList,
    )
}