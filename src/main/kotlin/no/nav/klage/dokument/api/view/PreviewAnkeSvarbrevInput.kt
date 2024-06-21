package no.nav.klage.dokument.api.view

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.klage.dokument.domain.dokumenterunderarbeid.Svarbrev
import no.nav.klage.oppgave.domain.klage.PartId
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class PreviewAnkeSvarbrevInput(
    val mottattKlageinstans: LocalDate,
    val sakenGjelder: PartId,
    val ytelseId: String,
    val svarbrev: Svarbrev,
    val klager: PartId?,
)