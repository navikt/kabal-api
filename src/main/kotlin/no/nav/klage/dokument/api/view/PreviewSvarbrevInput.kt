package no.nav.klage.dokument.api.view

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.klage.oppgave.domain.klage.SvarbrevSettings
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class PreviewSvarbrevInput(
    val mottattKlageinstans: LocalDate,
    val sakenGjelder: String,
    val ytelseId: String,
    val klager: String?,
    val typeId: String,
    val fullmektigFritekst: String?,
    val varsletBehandlingstidUnits: Int,
    val varsletBehandlingstidUnitType: SvarbrevSettings.BehandlingstidUnitType,
    val customText: String?,
    val title: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PreviewSvarbrevAnonymousInput(
    val ytelseId: String,
    val typeId: String,
    val behandlingstidUnits: Int,
    val behandlingstidUnitType: SvarbrevSettings.BehandlingstidUnitType,
    val customText: String?,
)