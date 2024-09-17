package no.nav.klage.oppgave.api.view

import java.util.*

data class SearchSaksnummerResponse(
    val aapneBehandlinger: List<UUID>,
    val avsluttedeBehandlinger: List<UUID>,
    val feilregistrerteBehandlinger: List<UUID>,
    val paaVentBehandlinger: List<UUID>,
)