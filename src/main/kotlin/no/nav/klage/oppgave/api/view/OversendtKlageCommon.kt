package no.nav.klage.oppgave.api.view

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.domain.klage.*

data class OversendtSakenGjelder(
    @Schema(
        required = true
    )
    val id: OversendtPartId,
    @Schema(
        description = "Ikke lenger i bruk",
        required = false,
        example = "true",
        deprecated = true,
    )
    val skalMottaKopi: Boolean
) {
    fun toSakenGjelder() = SakenGjelder(
        partId = id.toPartId(),
    )
}

data class OversendtKlager(
    @Schema(
        required = true
    )
    val id: OversendtPartId,
    @Schema(
        name = "klagersProsessfullmektig",
        description = "Kan settes dersom klager har en prosessfullmektig",
        required = false
    )
    val klagersProsessfullmektig: OversendtProsessfullmektig? = null
) {
    fun toKlagepart() = Klager(
        partId = id.toPartId(),
    )

    fun toProsessfullmektig(): Prosessfullmektig? {
        if (klagersProsessfullmektig == null) return null
        return klagersProsessfullmektig.toProsessfullmektig()
    }
}

data class OversendtProsessfullmektig(
    @Schema(
        required = true
    )
    val id: OversendtPartId,
    @Schema(
        description = "Ikke lenger i bruk",
        required = false,
        example = "true",
        deprecated = true,
    )
    val skalKlagerMottaKopi: Boolean
) {
    fun toProsessfullmektig() = Prosessfullmektig(
        partId = id.toPartId(), address = null, navn = null,

    )
}

data class OversendtPartId(
    @Schema(
        required = true,
        example = "PERSON / VIRKSOMHET"
    )
    val type: OversendtPartIdType,
    @Schema(
        required = true,
        example = "12345678910"
    )
    val verdi: String
) {
    fun toPartId() = PartId(
        type = type.toPartIdType(),
        value = verdi
    )
}

enum class OversendtPartIdType { PERSON, VIRKSOMHET }


data class OversendtDokumentReferanse(
    @Schema(
        required = true,
        example = "BRUKERS_KLAGE"
    )
    val type: MottakDokumentType,
    @Schema(
        required = true,
        example = "830498203"
    )
    val journalpostId: String
) {
    fun toMottakDokument() = MottakDokument(
        type = type,
        journalpostId = journalpostId
    )
}

data class OversendtSak(
    @Schema(
        required = false,
        example = "134132412"
    )
    val fagsakId: String,
    @Schema(
        required = true,
        example = "K9"
    )
    val fagsystem: Fagsystem
)

fun OversendtPartIdType.toPartIdType(): PartIdType =
    when (this) {
        OversendtPartIdType.PERSON -> PartIdType.PERSON
        OversendtPartIdType.VIRKSOMHET -> PartIdType.VIRKSOMHET
    }