package no.nav.klage.oppgave.api.view

import java.time.LocalDateTime

data class MedunderskriverFlytResponse (
    val modified: LocalDateTime,
    val medunderskriverFlyt: String
)
