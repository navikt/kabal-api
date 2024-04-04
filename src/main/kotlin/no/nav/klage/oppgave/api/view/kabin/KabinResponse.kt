package no.nav.klage.oppgave.api.view.kabin

import no.nav.klage.dokument.api.view.HandlingEnum
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
    val behandlingId: UUID,
)

data class CreatedKlageResponse(
    val behandlingId: UUID,
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
    val svarbrev: Svarbrev?,
) {
    data class Svarbrev(
        val dokumentUnderArbeidId: UUID,
        val title: String,
        val receivers: List<Receiver>,
    ) {
        data class Receiver(
            val part: BehandlingDetaljerView.PartViewWithUtsendingskanal,
            val overriddenAddress: BehandlingDetaljerView.Address?,
            val handling: HandlingEnum,
        )
    }
}

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
    val address: BehandlingDetaljerView.Address?,
    val utsendingskanal: BehandlingDetaljerView.Utsendingskanal,
    val language: String?,
)

data class OldKabinPartView(
    val id: String,
    val type: BehandlingDetaljerView.IdType,
    val name: String,
    val available: Boolean,
    val statusList: List<BehandlingDetaljerView.PartStatus>,
)

fun BehandlingDetaljerView.SakenGjelderViewWithUtsendingskanal.toKabinPartView(): KabinPartView {
    return KabinPartView(
        id = id,
        type = type,
        name = name,
        available = available,
        statusList = statusList,
        address = address,
        utsendingskanal = utsendingskanal,
        language = language,
    )
}

fun BehandlingDetaljerView.PartViewWithUtsendingskanal.toKabinPartView(): KabinPartView {
    return KabinPartView(
        id = id,
        type = type,
        name = name,
        available = available,
        statusList = statusList,
        address = address,
        utsendingskanal = utsendingskanal,
        language = language,
    )
}

fun BehandlingDetaljerView.PartView.toOldKabinPartView(): OldKabinPartView {
    return OldKabinPartView(
        id = id,
        type = type,
        name = name,
        available = available,
        statusList = statusList,
    )
}