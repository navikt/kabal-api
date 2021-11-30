package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.MedunderskriverFlyt
import java.time.LocalDateTime

data class MedunderskriverFlytResponse (
    val modified: LocalDateTime,
    val medunderskriverFlyt: MedunderskriverFlyt
)
