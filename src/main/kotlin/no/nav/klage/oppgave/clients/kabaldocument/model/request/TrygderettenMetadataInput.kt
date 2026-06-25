package no.nav.klage.oppgave.clients.kabaldocument.model.request

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.klage.oppgave.domain.behandling.embedded.PartId
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class TrygderettenMetadataInput(
    val kravfremsettelsesdato: LocalDate?,
    val paaanketVedtaksdato: LocalDate,
    val tidligereITROgOpphevetHenvist: Boolean?,
    val gjenopptak: Boolean?,
    val forsterketRett: Boolean,
    val ettersendelse: Boolean,
    val lovhenvisning: Set<String>,
    val representant: Representant?,
) {

    data class Representant(
        val partId: PartId?,
        val navn: String?,
        val adresse: AvsenderMottakerInput.Address?,
    )

}