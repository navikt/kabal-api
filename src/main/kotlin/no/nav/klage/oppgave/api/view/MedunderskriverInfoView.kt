package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.MedunderskriverFlyt

data class MedunderskriverView(
    val medunderskriver: SaksbehandlerView?
)

data class MedunderskriverFlytView(
    val medunderskriverFlyt: MedunderskriverFlyt
)