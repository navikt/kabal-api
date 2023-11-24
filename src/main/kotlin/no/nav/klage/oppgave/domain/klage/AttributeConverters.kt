package no.nav.klage.oppgave.domain.klage

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import no.nav.klage.kodeverk.*
import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel

@Converter
class DokumentTypeConverter : AttributeConverter<DokumentType, String?> {

    override fun convertToDatabaseColumn(entity: DokumentType?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): DokumentType? =
        id?.let { DokumentType.of(it) }
}

@Converter
class FagsystemConverter : AttributeConverter<Fagsystem, String?> {

    override fun convertToDatabaseColumn(entity: Fagsystem?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Fagsystem? =
        id?.let { Fagsystem.of(it) }
}

@Converter
class FlowStateConverter : AttributeConverter<FlowState, String?> {

    override fun convertToDatabaseColumn(entity: FlowState?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): FlowState? =
        id?.let { FlowState.of(it) }
}

@Converter
class PartIdTypeConverter : AttributeConverter<PartIdType, String?> {

    override fun convertToDatabaseColumn(entity: PartIdType?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): PartIdType? =
        id?.let { PartIdType.of(it) }
}

@Converter
class TemaConverter : AttributeConverter<Tema, String?> {

    override fun convertToDatabaseColumn(entity: Tema?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Tema? =
        id?.let { Tema.of(it) }
}

@Converter
class TypeConverter : AttributeConverter<Type, String?> {

    override fun convertToDatabaseColumn(entity: Type?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Type? =
        id?.let { Type.of(it) }
}

@Converter
class UtfallConverter : AttributeConverter<Utfall, String?> {

    override fun convertToDatabaseColumn(entity: Utfall?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Utfall? =
        id?.let { Utfall.of(it) }
}

@Converter
class YtelseConverter : AttributeConverter<Ytelse, String?> {

    override fun convertToDatabaseColumn(entity: Ytelse?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Ytelse? =
        id?.let { Ytelse.of(it) }
}

@Converter
class HjemmelConverter : AttributeConverter<Hjemmel, String?> {

    override fun convertToDatabaseColumn(entity: Hjemmel?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Hjemmel? =
        id?.let { Hjemmel.of(it) }
}

@Converter
class RegistreringshjemmelConverter : AttributeConverter<Registreringshjemmel, String?> {

    override fun convertToDatabaseColumn(entity: Registreringshjemmel?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Registreringshjemmel? =
        id?.let { Registreringshjemmel.of(it) }
}

@Converter
class FradelingReasonConverter : AttributeConverter<FradelingReason, String?> {

    override fun convertToDatabaseColumn(entity: FradelingReason?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): FradelingReason? =
        id?.let { FradelingReason.of(it) }
}