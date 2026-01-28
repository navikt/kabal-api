package no.nav.klage.oppgave.api.view.kabin

import no.nav.klage.dokument.api.view.HandlingEnum
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView.*
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

data class Mulighet(
    val behandlingId: UUID,
    val originalTypeId: String,
    val typeId: String,
    val sourceOfExistingAnkebehandling: List<ExistingAnkebehandling>,
    val existingBehandlingList: List<ExistingBehandling>,
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
    val gosysOppgaveRequired: Boolean,
)

data class ExistingAnkebehandling(
    val id: UUID,
    val created: LocalDateTime,
    val completed: LocalDateTime?,
)

data class ExistingBehandling(
    val typeId: String,
    val id: UUID,
    val created: LocalDateTime,
    val completed: LocalDateTime?,
)


data class CreatedBehandlingResponse(
    val behandlingId: UUID,
)

data class CreatedBehandlingStatusForKabin(
    val typeId: String,
    val ytelseId: String,
    val sakenGjelder: KabinPartView,
    val klager: KabinPartView,
    val fullmektig: KabinPartView?,
    val mottattVedtaksinstans: LocalDate?,
    val mottattKlageinstans: LocalDate,
    val frist: LocalDate,
    val varsletFrist: LocalDate?,
    val varsletFristUnits: Int?,
    val varsletFristUnitTypeId: String?,
    val fagsakId: String,
    val fagsystemId: String,
    val journalpost: DokumentReferanse,
    val tildeltSaksbehandler: TildeltSaksbehandler?,
    val svarbrev: KabinResponseSvarbrev?,
)

data class KabinResponseSvarbrev(
    val dokumentUnderArbeidId: UUID,
    val title: String,
    val receivers: List<Receiver>,
) {
    data class Receiver(
        val part: PartViewWithUtsendingskanal,
        val overriddenAddress: Address?,
        val handling: HandlingEnum,
    )
}

data class TildeltSaksbehandler(
    val navIdent: String,
    val navn: String,
)

data class KabinPartView(
    val identifikator: String,
    val type: IdType?,
    val name: String,
    val available: Boolean,
    val statusList: List<PartStatus>,
    val address: Address?,
    val utsendingskanal: Utsendingskanal,
    val language: String?,
)

fun SakenGjelderViewWithUtsendingskanal.toKabinPartView(): KabinPartView {
    return KabinPartView(
        identifikator = identifikator,
        type = type,
        name = name,
        available = available,
        statusList = statusList,
        address = address,
        utsendingskanal = utsendingskanal,
        language = language,
    )
}

fun PartViewWithUtsendingskanal.toKabinPartView(): KabinPartView {
    return KabinPartView(
        identifikator = identifikator!!, //if we come here, we know it is not null
        type = type,
        name = name,
        available = available,
        statusList = statusList,
        address = address,
        utsendingskanal = utsendingskanal,
        language = language,
    )
}

data class BehandlingIsDuplicateResponse(
    val fagsystemId: String,
    val kildereferanse: String,
    val typeId: String,
    val duplicate: Boolean,
)