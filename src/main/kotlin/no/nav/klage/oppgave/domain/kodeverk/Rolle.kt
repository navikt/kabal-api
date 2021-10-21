package no.nav.klage.oppgave.domain.kodeverk

import javax.persistence.AttributeConverter
import javax.persistence.Converter

enum class Rolle(override val id: String, override val navn: String, override val beskrivelse: String) : Kode {
    KLAGER("1", "Klager", "Klager"),
    SAKEN_GJELDER("2", "Saken gjelder", "Den saken gjelder"),
    PROSESSFULLMEKTIG("3", "Prosessfullmektig", "Prosessfullmektig"),
    RELEVANT_TREDJEPART("4", "Tredjepart", "Relevant tredjepart");

    override fun toString(): String {
        return "Rolle(id=$id, " +
                "navn=$navn)"
    }

    companion object {
        fun of(id: String): Rolle {
            return values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("No Rolle with ${id} exists")
        }
    }
}

@Converter
class RolleConverter : AttributeConverter<Rolle, String?> {

    override fun convertToDatabaseColumn(entity: Rolle?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Rolle? =
        id?.let { Rolle.of(it) }
}