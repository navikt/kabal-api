package no.nav.klage.dokument.api.view

import com.fasterxml.jackson.databind.JsonNode
import no.nav.klage.kodeverk.DokumentType
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.util.*

data class FilInput(
    val file: MultipartFile,
    val dokumentTypeId: String = DokumentType.NOTAT.id,
    val parentId: UUID?,
)

data class SmartHovedDokumentInput(
    val content: JsonNode?,
    val templateId: String?,
    val tittel: String?,
    val dokumentTypeId: String? = null,
    val version: Int?,
    val parentId: UUID?,
)

data class PatchSmartHovedDokumentInput(
    val content: JsonNode?,
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

data class DokumentTypeInput(val dokumentTypeId: String)

data class DatoMottattInput(val datoMottatt: LocalDate)

data class FerdigstillDokumentInput(
    val brevmottakerIds: Set<String>?,
)

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
    val overrideAddress: AddressInput?,
)

data class AddressInput(
    val adresselinje1: String,
    val adresselinje2: String?,
    val adresselinje3: String?,
    val landkode: String,
    val postnummer: String,
)