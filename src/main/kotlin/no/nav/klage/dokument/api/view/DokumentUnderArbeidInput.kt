package no.nav.klage.dokument.api.view

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.*

data class SmartHovedDokumentInput(
    val content: JsonNode,
    val data: String?,
    val templateId: String,
    val tittel: String?,
    val dokumentTypeId: String? = null,
    val parentId: UUID?,
    val language: DokumentView.Language = DokumentView.Language.NB,
)

data class PatchSmartHovedDokumentInput(
    val content: JsonNode,
    val data: String?,
    val version: Int?,
)

data class JournalfoerteDokumenterInput(
    val parentId: UUID,
    val journalfoerteDokumenter: Set<JournalfoertDokumentReference>,
)

data class JournalfoertDokumentReference(
    val journalpostId: String,
    val dokumentInfoId: String
)

data class OptionalPersistentDokumentIdInput(val dokumentId: UUID?)

data class DokumentTitleInput(val title: String)

data class LanguageInput(val language: DokumentView.Language)

data class DokumentTypeInput(val dokumentTypeId: String)

data class DatoMottattInput(val datoMottatt: LocalDate)

data class InngaaendeKanalInput(
    val kanal: InngaaendeKanal
)

enum class InngaaendeKanal {
    ALTINN_INNBOKS,
    E_POST,
}

data class AvsenderInput(
    val id: String
)

data class MottakerInput(
    val mottakerList: List<Mottaker>,
)

data class Mottaker(
    val id: String,
    val handling: HandlingEnum,
    val overriddenAddress: AddressInput?,
)

data class AddressInput(
    val adresselinje1: String?,
    val adresselinje2: String?,
    val adresselinje3: String?,
    val landkode: String,
    val postnummer: String?,
)