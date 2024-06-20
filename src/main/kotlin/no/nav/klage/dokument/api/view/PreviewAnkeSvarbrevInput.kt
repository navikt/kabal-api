package no.nav.klage.dokument.api.view

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.klage.oppgave.api.view.kabin.SvarbrevInput
import no.nav.klage.oppgave.domain.klage.PartId
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class PreviewAnkeSvarbrevInput(
    val mottattKlageinstans: LocalDate,
    val sakenGjelder: PartId,
    val ytelseId: String,
    val svarbrevInput: SvarbrevInput,
    val klager: PartId?,
)