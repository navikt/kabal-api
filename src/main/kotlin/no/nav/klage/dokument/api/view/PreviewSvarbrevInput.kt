package no.nav.klage.dokument.api.view

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.klage.kodeverk.TimeUnitType
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
    val varsletBehandlingstidUnitType: TimeUnitType?,
    val varsletBehandlingstidUnitTypeId: String?,
    val customText: String?,
    val title: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PreviewSvarbrevAnonymousInput(
    val ytelseId: String,
    val typeId: String,
    val behandlingstidUnits: Int,
    val behandlingstidUnitType: TimeUnitType?,
    val behandlingstidUnitTypeId: String?,
    val customText: String?,
)