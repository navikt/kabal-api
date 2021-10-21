package no.nav.klage.oppgave.domain.kodeverk

import javax.persistence.AttributeConverter
import javax.persistence.Converter

enum class Eoes(override val id: String, override val navn: String, override val beskrivelse: String) : Kode {

    RIKTIG("1", "Riktig", "Problemstilling knyttet til EØS/utland er godt håndtert"),
    IKKE_OPPDAGET("2", "Ikke oppdaget", "Vedtaksinstansen har ikke oppdaget at saken gjelder EØS/utland"),
    FEIL("3", "Feil", "Vedtaksinstansen har oppdaget at saken gjelder EØS/utland, men har håndtert saken feil"),
    UAKTUELT("4", "Uaktuelt", "EØS/utenlandsproblematikk er ikke relevant i saken");

    override fun toString(): String {
        return "Eoes(id=$id, " +
                "navn=$navn)"
    }

    companion object {
        fun of(id: String): Eoes {
            return values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("No Eoes with ${id} exists")
        }
    }
}

@Converter
class EoesConverter : AttributeConverter<Eoes, String?> {

    override fun convertToDatabaseColumn(entity: Eoes?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Eoes? =
        id?.let { Eoes.of(it) }
}

