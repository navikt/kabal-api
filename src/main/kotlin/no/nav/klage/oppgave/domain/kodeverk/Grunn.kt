package no.nav.klage.oppgave.domain.kodeverk

import javax.persistence.AttributeConverter
import javax.persistence.Converter

enum class Grunn(override val id: String, override val navn: String, override val beskrivelse: String) : Kode {

    MANGELFULL_UTREDNING("1", "Mangelfull utredning", "Mangelfull utredning"),
    ANDRE_SAKSBEHANDLINGSFEIL("2", "Andre saksbehandlingsfeil", "Andre saksbehandlingsfeil"),
    ENDRET_FAKTUM("3", "Endret faktum", "Endret faktum"),
    FEIL_I_BEVISVURDERINGEN("4", "Feil i bevisvurderingen", "Feil i bevisvurderingen"),
    FEIL_I_DEN_GENERELLE_LOVTOLKNINGEN("5", "Feil i den generelle lovtolkningen", "Feil i den generelle lovtolkningen"),
    FEIL_I_DEN_KONKRETE_RETTSANVENDELSEN(
        "6", "Feil i den konkrete rettsanvendelsen", "Feil i den konkrete rettsanvendelsen"
    );

    override fun toString(): String {
        return "Grunn(id=$id, " +
                "navn=$navn)"
    }

    companion object {
        fun of(id: String): Grunn {
            return values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("No Grunn with ${id} exists")
        }
    }

}

data class GrunnerPerUtfall(val utfall: Utfall, val grunner: List<Grunn>)

val grunnerPerUtfall: List<GrunnerPerUtfall> = listOf(
    GrunnerPerUtfall(Utfall.DELVIS_MEDHOLD, Grunn.values().asList()),
    GrunnerPerUtfall(Utfall.MEDHOLD, Grunn.values().asList()),
    GrunnerPerUtfall(Utfall.OPPHEVET, Grunn.values().asList()),
    GrunnerPerUtfall(Utfall.AVVIST, emptyList()),
    GrunnerPerUtfall(Utfall.OPPRETTHOLDT, emptyList()),
    GrunnerPerUtfall(Utfall.RETUR, emptyList()),
    GrunnerPerUtfall(Utfall.TRUKKET, emptyList()),
    GrunnerPerUtfall(Utfall.UGUNST, emptyList())
)

@Converter
class GrunnConverter : AttributeConverter<Grunn, String?> {

    override fun convertToDatabaseColumn(entity: Grunn?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Grunn? =
        id?.let { Grunn.of(it) }
}
