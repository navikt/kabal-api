package no.nav.klage.oppgave.api.view

import no.nav.klage.oppgave.domain.kodeverk.MedunderskriverFlyt

data class MedunderskriverInfoView (
    val navIdent: String?,
    val navn: String?,
    val medunderskriverFlyt: MedunderskriverFlyt
)
