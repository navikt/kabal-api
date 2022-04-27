package no.nav.klage.dokument.domain.kodeverk

import io.swagger.annotations.ApiModel
import javax.persistence.AttributeConverter
import javax.persistence.Converter

@ApiModel
enum class BrevmottakerType(override val id: String, override val navn: String, override val beskrivelse: String) :
    Kode {
    KLAGER("1", "Klager", "Klager"),
    SAKEN_GJELDER("2", "Saken gjelder", "Saken gjelder"),
    PROSESSFULLMEKTIG("3", "Prosessfullmektig", "Prosessfullmektig")
    ;

    override fun toString(): String {
        return "BrevmottakerRolle(id=$id, " +
                "navn=$navn)"
    }

    companion object {
        fun of(id: String): BrevmottakerType {
            return values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("No BremottakerType with $id exists")
        }

        fun fromNavn(navn: String): BrevmottakerType {
            return values().firstOrNull { it.navn == navn }
                ?: throw IllegalArgumentException("No BremottakerType with $navn exists")
        }
    }
}

@Converter
class BrevmottakerTypeConverter : AttributeConverter<BrevmottakerType, String?> {

    override fun convertToDatabaseColumn(entity: BrevmottakerType?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): BrevmottakerType? =
        id?.let { BrevmottakerType.of(it) }
}