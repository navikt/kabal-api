package no.nav.klage.oppgave.domain.kodeverk

import javax.persistence.AttributeConverter
import javax.persistence.Converter

enum class Hjemmel(
    override val id: String,
    val lov: LovKilde,
    val kapittelOgParagraf: KapittelOgParagraf? = null,
    override val navn: String,
    override val beskrivelse: String
) : Kode {

    /*
    //id = concat(lov,kapittel,paragraf,ledd,bokstav,punktum.
    Ref https://www.sprakradet.no/sprakhjelp/Skriveregler/Lovhenvisninger/
    Lov er fire tegn
    Kapittel er tre tegn
    Paragraf er tre tegn
    Ledd er to tegn
    Bokstav er to tegn
    Punktum er to tegn
    Ledd på slutten som ikke er brukt kan droppes. Ledd "inni" som ikke er brukt må settes til bare 0-tegn.

    Eksempler på oppbygde IDer:
    "1000.008.002" -> Folketrygdloven § 8-2
    "1000.008.002.001.001.001" -> Folketrygdloven § 8-2 første ledd bokstav a første punktum
    "1000.008.002.001.000.001" -> Folketrygdloven § 8-2 første ledd første punktum
    "1000.008.002.001.001" -> Folketrygdloven § 8-2 første ledd bokstav a
     */
    FTL("1000", LovKilde.FOLKETRYGDLOVEN, null, "FTL", "Folketrygdloven"),

    FTL_8_2("1000.008.002", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 2), "FTL 8-2", "Folketrygdloven §8-2"),
    FTL_8_3("1000.008.003", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 3), "FTL 8-3", "Folketrygdloven §8-3"),
    FTL_8_4("1000.008.004", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 4), "FTL 8-4", "Folketrygdloven §8-4"),
    FTL_8_7("1000.008.007", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 7), "FTL 8-7", "Folketrygdloven §8-7"),
    FTL_8_8("1000.008.008", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 8), "FTL 8-8", "Folketrygdloven §8-8"),
    FTL_8_12("1000.008.012", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 12), "FTL 8-12", "Folketrygdloven §8-12"),
    FTL_8_13("1000.008.013", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 13), "FTL 8-13", "Folketrygdloven §8-13"),
    FTL_8_15("1000.008.015", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 15), "FTL 8-15", "Folketrygdloven §8-15"),
    FTL_8_20("1000.008.020", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 20), "FTL 8-20", "Folketrygdloven §8-20"),
    FTL_8_28("1000.008.028", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 28), "FTL 8-28", "Folketrygdloven §8-28"),
    FTL_8_29("1000.008.029", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 29), "FTL 8-29", "Folketrygdloven §8-29"),
    FTL_8_30("1000.008.030", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 30), "FTL 8-30", "Folketrygdloven §8-30"),
    FTL_8_34("1000.008.034", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 34), "FTL 8-34", "Folketrygdloven §8-34"),
    FTL_8_35("1000.008.035", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 35), "FTL 8-35", "Folketrygdloven §8-35"),
    FTL_8_36("1000.008.036", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 36), "FTL 8-36", "Folketrygdloven §8-36"),
    FTL_8_37("1000.008.037", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 37), "FTL 8-37", "Folketrygdloven §8-37"),
    FTL_8_38("1000.008.038", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 38), "FTL 8-38", "Folketrygdloven §8-38"),
    FTL_8_39("1000.008.039", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 39), "FTL 8-39", "Folketrygdloven §8-39"),
    FTL_8_40("1000.008.040", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 40), "FTL 8-40", "Folketrygdloven §8-40"),
    FTL_8_41("1000.008.041", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 41), "FTL 8-41", "Folketrygdloven §8-41"),
    FTL_8_42("1000.008.042", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 42), "FTL 8-42", "Folketrygdloven §8-42"),
    FTL_8_43("1000.008.043", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 43), "FTL 8-43", "Folketrygdloven §8-43"),
    FTL_8_47("1000.008.047", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 47), "FTL 8-47", "Folketrygdloven §8-47"),
    FTL_8_49("1000.008.049", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(8, 49), "FTL 8-49", "Folketrygdloven §8-49"),
    FTL_22_3("1000.022.003", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(22, 3), "FTL 22-3", "Folketrygdloven §22-3"),
    FTL_22_12(
        "1000.022.012",
        LovKilde.FOLKETRYGDLOVEN,
        KapittelOgParagraf(22, 12),
        "FTL 22-12",
        "Folketrygdloven §22-12"
    ),
    FTL_22_13(
        "1000.022.013",
        LovKilde.FOLKETRYGDLOVEN,
        KapittelOgParagraf(22, 13),
        "FTL 22-13",
        "Folketrygdloven §22-13"
    ),
    FTL_9("1000.009", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9), "FTL 9", "Folketrygdloven kapittel 9"),
    FTL_9_2("1000.009.002", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 2), "FTL 9-2", "Folketrygdloven §9-2"),
    FTL_9_3("1000.009.003", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 3), "FTL 9-3", "Folketrygdloven §9-3"),
    FTL_9_5("1000.009.005", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 5), "FTL 9-5", "Folketrygdloven §9-5"),
    FTL_9_6("1000.009.006", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 6), "FTL 9-6", "Folketrygdloven §9-6"),
    FTL_9_8("1000.009.008", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 8), "FTL 9-8", "Folketrygdloven §9-8"),
    FTL_9_9("1000.009.009", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 9), "FTL 9-9", "Folketrygdloven §9-9"),
    FTL_9_10("1000.009.010", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 10), "FTL 9-10", "Folketrygdloven §9-10"),
    FTL_9_11("1000.009.011", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 11), "FTL 9-11", "Folketrygdloven §9-11"),
    FTL_9_12("1000.009.012", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 12), "FTL 9-12", "Folketrygdloven §9-12"),
    FTL_9_13("1000.009.013", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 13), "FTL 9-13", "Folketrygdloven §9-13"),
    FTL_9_14("1000.009.014", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 14), "FTL 9-14", "Folketrygdloven §9-14"),
    FTL_9_15("1000.009.015", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 15), "FTL 9-15", "Folketrygdloven §9-15"),
    FTL_9_16("1000.009.016", LovKilde.FOLKETRYGDLOVEN, KapittelOgParagraf(9, 16), "FTL 9-16", "Folketrygdloven §9-16"),

    MANGLER("1002", LovKilde.UKJENT, null, "MANGLER", "Hjemmel mangler")
    ;

    fun toSearchableString(): String {
        if (kapittelOgParagraf == null) {
            return lov.name
        }
        if (kapittelOgParagraf.paragraf == null) {
            return "${lov.name} ${kapittelOgParagraf.kapittel}"
        }
        return "${lov.name} ${kapittelOgParagraf.kapittel}-${kapittelOgParagraf.paragraf}"
    }

    companion object {
        fun of(id: String): Hjemmel {
            return values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("No Hjemmel with ${id} exists")
        }

        fun of(lov: LovKilde, kapittelOgParagraf: KapittelOgParagraf?): Hjemmel {
            return values().firstOrNull { it.lov == lov && it.kapittelOgParagraf == kapittelOgParagraf }
                ?: throw IllegalArgumentException("No Hjemmel with lov $lov and kapittelOgParagraf $kapittelOgParagraf exists")
        }
    }

}

data class KapittelOgParagraf(val kapittel: Int, val paragraf: Int? = null)

enum class LovKilde {
    FOLKETRYGDLOVEN, FORVALTNINGSLOVEN, UKJENT
}

data class HjemlerPerTema(val tema: Tema, val hjemler: List<Hjemmel>)

val hjemlerPerTema: List<HjemlerPerTema> = listOf(
    HjemlerPerTema(
        Tema.OMS,
        Hjemmel.values().filter { it.kapittelOgParagraf != null && it.kapittelOgParagraf.kapittel == 9 }
                + Hjemmel.FTL + Hjemmel.MANGLER
    ),
    HjemlerPerTema(
        Tema.SYK,
        Hjemmel.values().filter { it.kapittelOgParagraf != null && it.kapittelOgParagraf.kapittel == 8 }
                + Hjemmel.FTL + Hjemmel.MANGLER
    )
)

@Converter
class HjemmelConverter : AttributeConverter<Hjemmel, String?> {

    override fun convertToDatabaseColumn(entity: Hjemmel?): String? =
        entity?.let { it.id }

    override fun convertToEntityAttribute(id: String?): Hjemmel? =
        id?.let { Hjemmel.of(it) }
}
