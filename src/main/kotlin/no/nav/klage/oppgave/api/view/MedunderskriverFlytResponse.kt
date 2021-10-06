package no.nav.klage.oppgave.api.view

import no.nav.klage.oppgave.domain.kodeverk.MedunderskriverFlyt
import java.time.LocalDateTime

data class MedunderskriverFlytResponse (
    val modified: LocalDateTime,
    val medunderskriverFlyt: MedunderskriverFlyt
)
