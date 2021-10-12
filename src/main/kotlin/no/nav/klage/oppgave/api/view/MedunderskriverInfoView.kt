package no.nav.klage.oppgave.api.view

import no.nav.klage.oppgave.domain.kodeverk.MedunderskriverFlyt

data class MedunderskriverInfoView (
    val medunderskriver: SaksbehandlerRefView? = null,
    val medunderskriverFlyt: MedunderskriverFlyt = MedunderskriverFlyt.IKKE_SENDT
)
