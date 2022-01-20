package no.nav.klage.oppgave.domain.kodeverk

import no.nav.klage.kodeverk.Type
import org.springframework.core.env.Environment
import java.util.*

object LovligeTyper {
    private val lovligeTyperIProdGcp = EnumSet.of(Type.KLAGE)
    private val lovligeTyperIDevGcp = EnumSet.of(Type.KLAGE, Type.ANKE)
    
    fun lovligeTyper(environment: Environment): EnumSet<Type> = if (environment.activeProfiles.contains("prod-gcp")) {
        lovligeTyperIProdGcp
    } else {
        lovligeTyperIDevGcp
    }
}
