package no.nav.klage.oppgave.domain.kodeverk

import no.nav.klage.kodeverk.Ytelse
import org.springframework.core.env.Environment
import java.util.*

object LovligeYtelser {
    private val lovligeYtelserIProdGcp = EnumSet.of(Ytelse.OMS_OMP, Ytelse.OMS_OLP, Ytelse.OMS_PSB, Ytelse.OMS_PLS)
    private val lovligeYtelserIDevGcp =
        EnumSet.of(Ytelse.OMS_OMP, Ytelse.OMS_OLP, Ytelse.OMS_PSB, Ytelse.OMS_PLS, Ytelse.SYK_SYK)

    fun lovligeYtelser(environment: Environment): EnumSet<Ytelse> =
        if (environment.activeProfiles.contains("prod-gcp")) {
            lovligeYtelserIProdGcp
        } else {
            lovligeYtelserIDevGcp
        }
}
