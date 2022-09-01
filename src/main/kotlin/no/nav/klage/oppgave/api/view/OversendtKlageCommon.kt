package no.nav.klage.oppgave.api.view

import io.swagger.v3.oas.annotations.media.Schema
import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.hjemmel.LovKilde
import no.nav.klage.oppgave.domain.klage.*


fun KildeFagsystem.mapFagsystem(): Fagsystem =
    when (this) {
        KildeFagsystem.AO01 -> Fagsystem.AO01
        KildeFagsystem.FS36 -> Fagsystem.FS36
        KildeFagsystem.AO11 -> Fagsystem.AO11
        KildeFagsystem.BISYS -> Fagsystem.BISYS
        KildeFagsystem.FS38 -> Fagsystem.FS38
        KildeFagsystem.IT01 -> Fagsystem.IT01
        KildeFagsystem.K9 -> Fagsystem.K9
        KildeFagsystem.OB36 -> Fagsystem.OB36
        KildeFagsystem.OEBS -> Fagsystem.OEBS
        KildeFagsystem.PP01 -> Fagsystem.PP01
        KildeFagsystem.UFM -> Fagsystem.UFM
        KildeFagsystem.BA -> Fagsystem.BA
        KildeFagsystem.EF -> Fagsystem.EF
        KildeFagsystem.KONT -> Fagsystem.KONT
        KildeFagsystem.SUPSTONAD -> Fagsystem.SUPSTONAD
        KildeFagsystem.OMSORGSPENGER -> Fagsystem.OMSORGSPENGER
        KildeFagsystem.MANUELL -> Fagsystem.MANUELL
    }

class HjemmelFraFoersteInstans private constructor(
    @Schema(
        required = false,
        example = "9"
    )
    val kapittel: Int?,
    @Schema(
        required = false,
        example = "1"
    )
    val paragraf: Int?,
    @Schema(
        required = true
    )
    val lov: Lov
) {
    constructor(lov: Lov, kapittel: Int, paragraf: Int) : this(kapittel, paragraf, lov)

    override fun toString(): String {
        if (kapittel != null && paragraf != null) {
            return "$lov $kapittel-$paragraf"
        } else if (kapittel != null) {
            return "$lov $kapittel"
        } else {
            return "$lov"
        }
    }

    enum class Lov {
        FOLKETRYGDLOVEN, FORVALTNINGSLOVEN
    }

    fun Lov.toLovKilde(): LovKilde =
        when (this) {
            Lov.FOLKETRYGDLOVEN -> LovKilde.FOLKETRYGDLOVEN
            Lov.FORVALTNINGSLOVEN -> LovKilde.FORVALTNINGSLOVEN
        }
}

data class OversendtSakenGjelder(
    @Schema(
        required = true
    )
    val id: OversendtPartId,
    @Schema(
        required = true,
        example = "true"
    )
    val skalMottaKopi: Boolean
) {
    fun toSakenGjelder() = SakenGjelder(
        partId = id.toPartId(),
        skalMottaKopi = skalMottaKopi
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
        prosessfullmektig = klagersProsessfullmektig?.toProsessfullmektig()
    )
}

data class OversendtProsessfullmektig(
    @Schema(
        required = true
    )
    val id: OversendtPartId,
    @Schema(
        required = true,
        example = "true"
    )
    val skalKlagerMottaKopi: Boolean
) {
    fun toProsessfullmektig() = Prosessfullmektig(
        partId = id.toPartId(),
        skalPartenMottaKopi = skalKlagerMottaKopi
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
    val fagsakId: String? = null,
    @Schema(
        required = true,
        example = "K9"
    )
    val fagsystem: KildeFagsystem
)

@Schema
enum class KildeFagsystem {
    FS36,
    AO01,
    AO11,
    BISYS,
    FS38,
    IT01,
    K9,
    OB36,
    OEBS,
    PP01,
    UFM,
    BA,
    EF,
    KONT,
    SUPSTONAD,
    OMSORGSPENGER,
    MANUELL
}


fun OversendtPartIdType.toPartIdType(): PartIdType =
    when (this) {
        OversendtPartIdType.PERSON -> PartIdType.PERSON
        OversendtPartIdType.VIRKSOMHET -> PartIdType.VIRKSOMHET
    }