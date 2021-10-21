package no.nav.klage.oppgave.domain.kodeverk

import io.swagger.annotations.ApiModel
import org.springframework.core.env.Environment
import java.util.*
import javax.persistence.AttributeConverter
import javax.persistence.Converter

@ApiModel
enum class Tema(override val id: String, override val navn: String, override val beskrivelse: String) : Kode {

    AAP("1", "AAP", "Arbeidsavklaringspenger"),
    AAR("2", "AAR", "Aa-registeret"),
    AGR("3", "AGR", "Ajourhold - Grunnopplysninger"),
    BAR("4", "BAR", "Barnetrygd"),
    BID("5", "BID", "Bidrag"),
    BIL("6", "BIL", "Bil"),
    DAG("7", "DAG", "Dagpenger"),
    ENF("8", "ENF", "Enslig forsørger"),
    ERS("9", "ERS", "Erstatning"),
    FAR("10", "FAR", "Farskap"),
    FEI("11", "FEI", "Feilutbetaling"),
    FOR("12", "FOR", "Foreldre- og svangerskapspenger"),
    FOS("13", "FOS", "Forsikring"),
    FRI("14", "FRI", "Kompensasjon for selvstendig næringsdrivende/frilansere"),
    FUL("15", "FUL", "Fullmakt"),
    GEN("16", "GEN", "Generell"),
    GRA("17", "GRA", "Gravferdsstønad"),
    GRU("18", "GRU", "Grunn- og hjelpestønad"),
    HEL("19", "HEL", "Helsetjenester og ortopediske hjelpemidler"),
    HJE("20", "HJE", "Hjelpemidler"),
    IAR("21", "IAR", "Inkluderende arbeidsliv"),
    IND("22", "IND", "Tiltakspenger"),
    KON("23", "KON", "Kontantstøtte"),
    KTR("24", "KTR", "Kontroll"),
    MED("25", "MED", "Medlemskap"),
    MOB("26", "MOB", "Mobilitetsfremmende stønad"),
    OMS("27", "OMS", "Omsorgspenger, pleiepenger og opplæringspenger"),
    OPA("28", "OPA", "Oppfølging - Arbeidsgiver"),
    OPP("29", "OPP", "Oppfølging"),
    PEN("30", "PEN", "Pensjon"),
    PER("31", "PER", "Permittering og masseoppsigelser"),
    REH("32", "REH", "Rehabilitering"),
    REK("33", "REK", "Rekruttering og stilling"),
    RPO("34", "RPO", "Retting av personopplysninger"),
    RVE("35", "RVE", "Rettferdsvederlag"),
    SAA("36", "SAA", "Sanksjon - Arbeidsgiver"),
    SAK("37", "SAK", "Saksomkostninger"),
    SAP("38", "SAP", "Sanksjon - Person"),
    SER("39", "SER", "Serviceklager"),
    SIK("40", "SIK", "Sikkerhetstiltak"),
    STO("41", "STO", "Regnskap/utbetaling"),
    SUP("42", "SUP", "Supplerende stønad"),
    SYK("43", "SYK", "Sykepenger"),
    SYM("44", "SYM", "Sykmeldinger"),
    TIL("45", "TIL", "Tiltak"),
    TRK("46", "TRK", "Trekkhåndtering"),
    TRY("47", "TRY", "Trygdeavgift"),
    TSO("48", "TSO", "Tilleggsstønad"),
    TSR("49", "TSR", "Tilleggsstønad arbeidssøkere"),
    UFM("50", "UFM", "Unntak fra medlemskap"),
    UFO("51", "UFO", "Uføretrygd"),
    UKJ("52", "UKJ", "Ukjent"),
    VEN("53", "VEN", "Ventelønn"),
    YRA("54", "YRA", "Yrkesrettet attføring"),
    YRK("55", "YRK", "Yrkesskade / Menerstatning"),
    GOS("56", "GOS", "Gosys") //Er ikke egentlig et tema, men returneres fra Axsys likevel
    ;

    override fun toString(): String {
        return "Tema(id=$id, " +
                "navn=$navn)"
    }

    companion object {
        fun of(id: String): Tema {
            return values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("No Tema with $id exists")
        }

        fun fromNavn(navn: String?): Tema {
            return values().firstOrNull { it.navn == navn }
                ?: throw IllegalArgumentException("No Tema with $navn exists")
        }
    }
}

object TemaTilgjengeligeForEktefelle {
    private val ektefelleTemaerIProdGcp = EnumSet.of(Tema.OMS)
    private val ektefelleTemaerIDevGcp = EnumSet.of(Tema.OMS)

    fun temaerTilgjengeligForEktefelle(environment: Environment): EnumSet<Tema> =
        if (environment.activeProfiles.contains("prod-gcp")) {
            ektefelleTemaerIProdGcp
        } else {
            ektefelleTemaerIDevGcp
        }
}

object LovligeTemaer {
    private val lovligeTemaerIProdGcp = EnumSet.of(Tema.OMS)
    private val lovligeTemaerIDevGcp = EnumSet.of(Tema.OMS, Tema.SYK)

    fun lovligeTemaer(environment: Environment): EnumSet<Tema> = if (environment.activeProfiles.contains("prod-gcp")) {
        lovligeTemaerIProdGcp
    } else {
        lovligeTemaerIDevGcp
    }
}

@Converter
class TemaConverter : AttributeConverter<Tema, String?> {

    override fun convertToDatabaseColumn(entity: Tema?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Tema? =
        id?.let { Tema.of(it) }
}
