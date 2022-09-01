package no.nav.klage.oppgave.domain.kodeverk

import io.swagger.v3.oas.annotations.media.Schema
import javax.persistence.AttributeConverter
import javax.persistence.Converter

@Schema
enum class SivilstandType(override val id: String, override val navn: String, override val beskrivelse: String) : Kode {

    GIFT("1", "Gift", "Gift"),
    REGISTRERT_PARTNER("2", "Registrert partner", "Registrert partner")
    ;

    override fun toString(): String {
        return "SivilstandType(id=$id, " +
                "navn=$navn)"
    }

    companion object {
        fun of(id: String): SivilstandType {
            return values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("No SivilstandType with $id exists")
        }

        fun fromNavn(navn: String): SivilstandType {
            return values().firstOrNull { it.navn == navn }
                ?: throw IllegalArgumentException("No SivilstandType with $navn exists")
        }
    }
}


@Converter
class SivilstandTypeConverter : AttributeConverter<SivilstandType, String?> {

    override fun convertToDatabaseColumn(entity: SivilstandType?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): SivilstandType? =
        id?.let { SivilstandType.of(it) }
}
