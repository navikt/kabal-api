package no.nav.klage.dokument.api.view

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Svarbrev
import no.nav.klage.oppgave.domain.klage.PartId
import java.time.LocalDate
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.domain.klage.SvarbrevSettings

@JsonIgnoreProperties(ignoreUnknown = true)
data class PreviewSvarbrevInput(
    val mottattKlageinstans: LocalDate,
    val sakenGjelder: PartId,
    val ytelseId: String,
    val svarbrev: Svarbrev,
    val klager: PartId?,
    val typeId: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PreviewSvarbrevAnonymousInput(
    val ytelseId: String,
    val typeId: String,
    val behandlingstidUnits: Int,
    val behandlingstidUnitType: SvarbrevSettings.BehandlingstidUnitType,
    val customText: String?,
)