package no.nav.klage.oppgave.clients.kodeverk

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDate

@JsonIgnoreProperties(ignoreUnknown = true)
data class KodeverkResponse (
    val betydninger: Map<String, List<Betydning>>
)

data class Betydning(
    val gyldigFra: LocalDate,
    val gyldigTil: LocalDate,
    val beskrivelser: Map<String, Beskrivelse>
)

data class Beskrivelse(
    val term: String,
    val tekst: String,
)