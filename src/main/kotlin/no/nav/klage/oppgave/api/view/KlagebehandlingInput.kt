package no.nav.klage.oppgave.api.view

data class KlagebehandlingMedunderskriveridentInput(
    val medunderskriverident: String?,
    val klagebehandlingVersjon: Long = 1L
)
