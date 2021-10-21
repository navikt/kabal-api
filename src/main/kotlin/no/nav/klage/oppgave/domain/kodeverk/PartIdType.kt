package no.nav.klage.oppgave.domain.kodeverk

import javax.persistence.AttributeConverter
import javax.persistence.Converter

enum class PartIdType(override val id: String, override val navn: String, override val beskrivelse: String) : Kode {
    PERSON("PERSON", "Person", "Person"),
    VIRKSOMHET("VIRKSOMHET", "Virksomhet", "Virksomhet");

    override fun toString(): String {
        return "PartIdType(id=$id, " +
                "navn=$navn)"
    }

    companion object {
        fun of(id: String): PartIdType {
            return values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("No PartIdType with ${id} exists")
        }
    }
}

@Converter
class PartIdTypeConverter : AttributeConverter<PartIdType, String?> {

    override fun convertToDatabaseColumn(entity: PartIdType?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): PartIdType? =
        id?.let { PartIdType.of(it) }
}