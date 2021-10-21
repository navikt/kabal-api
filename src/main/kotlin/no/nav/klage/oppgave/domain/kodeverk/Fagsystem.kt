package no.nav.klage.oppgave.domain.kodeverk

import javax.persistence.AttributeConverter
import javax.persistence.Converter

enum class Fagsystem(override val id: String, override val navn: String, override val beskrivelse: String) : Kode {
    FS36("1", "FS36", "Vedtaksløsning Foreldrepenger"),
    //MERK: FS39 er ikke lenger i bruk, og kan anses som utgått.
    FS39("2", "FS39", "Saksbehandling for Folketrygdloven kapittel 9"),
    AO01("3", "AO01", "Arena"),
    AO11("4", "AO11", "Grisen"),
    BISYS("5", "BISYS", "BISYS"),
    FS38("6", "FS38", "Melosys"),
    IT01("7", "IT01", "Infotrygd"),
    K9("8", "K9", "Sykdom i familien"),
    OB36("9", "OB36", "Utbetalingsmelding"),
    OEBS("10", "OEBS", "OEBS"),
    PP01("11", "PP01", "Pesys"),
    UFM("12", "UFM", "Unntak fra medlemskap"),
    BA("13", "BA", "Barnetrygd"),
    EF("14", "EF", "Enslig forsørger"),
    KONT("15", "KONT", "Kontantstøtte"),
    SUPSTONAD("16", "SUPSTONAD", "Supplerende Stønad"),
    OMSORGSPENGER("17", "OMSORGSPENGER", "Omsorgspenger"),
    MANUELL("18", "MANUELL", "Manuell registrering av kvalitetsskjema");

    companion object {
        fun of(id: String): Fagsystem {
            return values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("No Fagsystem with $id exists")
        }

        fun fromNavn(navn: String): Fagsystem {
            return values().firstOrNull { it.navn == navn }
                ?: throw IllegalArgumentException("No Fagsystem with $navn exists")
        }
    }
}

@Converter
class FagsystemConverter : AttributeConverter<Fagsystem, String?> {

    override fun convertToDatabaseColumn(entity: Fagsystem?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Fagsystem? =
        id?.let { Fagsystem.of(it) }
}