package no.nav.klage.oppgave.domain.kodeverk

import no.nav.klage.kodeverk.Tema
import org.springframework.core.env.Environment
import java.util.*

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

