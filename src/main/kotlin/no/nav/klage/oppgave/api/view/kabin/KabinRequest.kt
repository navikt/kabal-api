package no.nav.klage.oppgave.api.view.kabin

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.TimeUnitType
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import java.time.LocalDate
import java.util.*

data class GetCompletedBehandlingerInput(
    val idnummer: String
)

data class SearchUsedJournalpostIdInput(
    val fnr: String,
)

data class BehandlingIsDuplicateInput(
    val fagsystemId: String,
    val kildereferanse: String,
    val typeId: String
)

data class GosysOppgaveIsDuplicateInput(
    val gosysOppgaveId: Long,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateBehandlingBasedOnKabinInputWithPreviousKabalBehandling(
    val typeId: String,
    val sourceBehandlingId: UUID,
    val mottattNav: LocalDate,
    val frist: LocalDate,
    val klager: OversendtPartId?,
    val fullmektig: OversendtPartId?,
    val receivedDocumentJournalpostId: String,
    val saksbehandlerIdent: String?,
    val svarbrevInput: SvarbrevInput,
    val hjemmelIdList: List<String>,
    val gosysOppgaveId: Long?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateAnkeBasedOnCompleteKabinInput(
    val sakenGjelder: OversendtPartId,
    val klager: OversendtPartId?,
    val fullmektig: OversendtPartId?,
    val fagsakId: String,
    val fagsystemId: String,
    val hjemmelIdList: List<String>,
    val forrigeBehandlendeEnhet: String,
    val ankeJournalpostId: String,
    val mottattNav: LocalDate,
    val frist: LocalDate,
    val ytelseId: String,
    val kildereferanse: String,
    val saksbehandlerIdent: String?,
    val svarbrevInput: SvarbrevInput,
    val gosysOppgaveId: Long,
)
data class OversendtPartId(
    val type: OversendtPartIdType,
    val value: String
)

fun OversendtPartId.toPartId(): PartId {
    return PartId(
        type = PartIdType.of(type.name),
        value = value
    )
}

enum class OversendtPartIdType { PERSON, VIRKSOMHET }

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateKlageBasedOnKabinInput(
    val sakenGjelder: OversendtPartId,
    val klager: OversendtPartId?,
    val fullmektig: OversendtPartId?,
    val fagsakId: String,
    val fagsystemId: String,
    val hjemmelIdList: List<String>,
    val forrigeBehandlendeEnhet: String,
    val klageJournalpostId: String,
    val brukersHenvendelseMottattNav: LocalDate,
    val sakMottattKa: LocalDate,
    val frist: LocalDate,
    val ytelseId: String,
    val kildereferanse: String,
    val saksbehandlerIdent: String?,
    val svarbrevInput: SvarbrevInput,
    val gosysOppgaveId: Long,
)

data class CreateBehandlingBasedOnJournalpostInput(
    val typeId: String? = Type.OMGJOERINGSKRAV.id,
    val sakenGjelder: OversendtPartId,
    val klager: OversendtPartId?,
    val fullmektig: OversendtPartId?,
    val fagsakId: String,
    val fagsystemId: String,
    val hjemmelIdList: List<String>,
    val forrigeBehandlendeEnhet: String,
    val receivedDocumentJournalpostId: String,
    val mottattNav: LocalDate,
    val frist: LocalDate,
    val ytelseId: String,
    val kildereferanse: String,
    val saksbehandlerIdent: String?,
    val svarbrevInput: SvarbrevInput,
    val gosysOppgaveId: Long,
)

data class SvarbrevInput(
    val title: String,
    val receivers: List<Receiver>,
    val fullmektigFritekst: String?,
    val initialCustomText: String?,
    val customText: String?,
    val varsletBehandlingstidUnits: Int,
    val varsletBehandlingstidUnitTypeId: String?,
    val varsletBehandlingstidUnitType: TimeUnitType?,
    val doNotSendLetter: Boolean = false,
    val reasonNoLetter: String?,
) {

    init {
        if (doNotSendLetter) {
            require(!reasonNoLetter.isNullOrBlank()) {
                "reasonNoLetter must be provided when doNotSendLetter is true"
            }
        } else {
            require(reasonNoLetter == null) { "reasonNoLetter must be null when doNotSendLetter is false" }
        }
    }

    data class Receiver(
        val identifikator: String?,
        val handling: HandlingEnum,
        val overriddenAddress: AddressInput?,
        val navn: String?,
    ) {
        data class AddressInput(
            val adresselinje1: String?,
            val adresselinje2: String?,
            val adresselinje3: String?,
            val landkode: String,
            val postnummer: String?,
        )

        enum class HandlingEnum {
            AUTO,
            LOCAL_PRINT,
            CENTRAL_PRINT
        }
    }
}