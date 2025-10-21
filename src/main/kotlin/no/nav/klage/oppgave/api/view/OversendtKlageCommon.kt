package no.nav.klage.oppgave.api.view

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.oppgave.domain.behandling.embedded.Klager
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import no.nav.klage.oppgave.domain.behandling.embedded.Prosessfullmektig
import no.nav.klage.oppgave.domain.behandling.embedded.SakenGjelder
import no.nav.klage.oppgave.domain.behandling.subentities.MottakDokumentDTO
import no.nav.klage.oppgave.domain.behandling.subentities.MottakDokumentType
import java.util.*

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
)

data class OversendtKlagerLegacy(
    @Schema(
        required = true
    )
    val id: OversendtPartId,
    @Schema(
        name = "klagersProsessfullmektig",
        description = "Kan settes dersom klager har en prosessfullmektig",
        required = false
    )
    val klagersProsessfullmektig: OversendtProsessfullmektigLegacy? = null
)

data class OversendtProsessfullmektigLegacy(
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
        id = UUID.randomUUID(),
        partId = id.toPartId(),
        address = null,
        navn = null,
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
    fun toMottakDokument() = MottakDokumentDTO(
        type = type,
        journalpostId = journalpostId
    )
}

data class OversendtSak(
    @Schema(
        required = true,
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

fun getParts(sakenGjelder: OversendtSakenGjelder?, klager: OversendtKlagerLegacy): Triple<SakenGjelder, Klager, Prosessfullmektig?> {
    val klagePart = Klager(
        id = UUID.randomUUID(),
        partId = klager.id.toPartId(),
    )

    val sakenGjelderPart = if (sakenGjelder?.id?.verdi == klager.id.verdi || sakenGjelder == null) {
        SakenGjelder(
            id = klagePart.id,
            partId = klagePart.partId,
        )
    } else {
        SakenGjelder(
            id = UUID.randomUUID(),
            partId = sakenGjelder.id.toPartId(),
        )
    }

    val prosessfullmektigPart = if (klager.klagersProsessfullmektig?.id?.verdi == klager.id.verdi) {
        Prosessfullmektig(
            id = klagePart.id,
            partId = klagePart.partId,
            address = null,
            navn = null,
        )
    } else if (klager.klagersProsessfullmektig != null) {
        klager.klagersProsessfullmektig.toProsessfullmektig()
    } else null

    return Triple(sakenGjelderPart, klagePart, prosessfullmektigPart)
}