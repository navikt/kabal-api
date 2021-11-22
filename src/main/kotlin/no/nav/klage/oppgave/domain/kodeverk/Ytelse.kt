package no.nav.klage.oppgave.domain.kodeverk

import org.springframework.core.env.Environment
import java.util.*
import javax.persistence.AttributeConverter
import javax.persistence.Converter

enum class Ytelse(override val id: String, override val navn: String, override val beskrivelse: String) : Kode {
    OMS_OMP("1", "Omsorgspenger", "Omsorgspenger"),
    OMS_OLP("2", "Opplæringspenger", "Opplæringspenger"),
    OMS_PSB("3", "Pleiepenger sykt barn", "Pleiepenger sykt barn"),
    OMS_PLS("4", "Pleiepenger i livets sluttfase", "Pleiepenger i livets sluttfase"),
    SYK_SYK("5", "Sykepenger", "Sykepenger")
    ;


    companion object {
        fun of(id: String): Ytelse {
            return values().firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("No Ytelse with id $id exists")
        }
    }

    fun toTema(): Tema {
        return when(this) {
            OMS_OMP, OMS_OLP, OMS_PSB, OMS_PLS -> Tema.OMS
            SYK_SYK -> Tema.SYK
        }
    }
}

object LovligeYtelser {
    private val lovligeYtelserIProdGcp = EnumSet.of(Ytelse.OMS_OMP, Ytelse.OMS_OLP, Ytelse.OMS_PSB, Ytelse.OMS_PLS)
    private val lovligeYtelserIDevGcp = EnumSet.of(Ytelse.OMS_OMP, Ytelse.OMS_OLP, Ytelse.OMS_PSB, Ytelse.OMS_PLS, Ytelse.SYK_SYK)

    fun lovligeYtelser(environment: Environment): EnumSet<Ytelse> = if (environment.activeProfiles.contains("prod-gcp")) {
        lovligeYtelserIProdGcp
    } else {
        lovligeYtelserIDevGcp
    }
}

@Converter
class YtelseConverter : AttributeConverter<Ytelse, String?> {

    override fun convertToDatabaseColumn(entity: Ytelse?): String? =
        entity?.id

    override fun convertToEntityAttribute(id: String?): Ytelse? =
        id?.let { Ytelse.of(it) }
}