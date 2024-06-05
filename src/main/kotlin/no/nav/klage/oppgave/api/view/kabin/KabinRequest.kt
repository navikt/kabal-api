package no.nav.klage.oppgave.api.view.kabin

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.domain.klage.PartId
import java.time.LocalDate
import java.util.*

data class GetCompletedKlagebehandlingerInput(
    val idnummer: String
)

data class SearchUsedJournalpostIdInput(
    val fnr: String,
)

data class IsDuplicateInput(
    val fagsystemId: String,
    val kildereferanse: String,
    val typeId: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateAnkeBasedOnKabinInput(
    val sourceBehandlingId: UUID,
    val mottattNav: LocalDate,
    val frist: LocalDate,
    val klager: OversendtPartId?,
    val fullmektig: OversendtPartId?,
    val ankeDocumentJournalpostId: String,
    val saksbehandlerIdent: String?,
    val svarbrevInput: SvarbrevInput?,
    val hjemmelIdList: List<String>,
    val oppgaveId: Long?,
) {
    data class OversendtPartId(
        val type: OversendtPartIdType,
        val value: String
    )

    enum class OversendtPartIdType { PERSON, VIRKSOMHET }
}

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
    val svarbrevInput: SvarbrevInput?,
    val oppgaveId: Long?,
) {
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
}

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
    val oppgaveId: Long?,
) {
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
}

data class SvarbrevInput(
    val title: String = "Anke - orientering om saksbehandlingstid",
    val receivers: List<Receiver>,
    val fullmektigFritekst: String?,
) {
    data class Receiver(
        val id: String,
        val handling: HandlingEnum,
        val overriddenAddress: AddressInput?,
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