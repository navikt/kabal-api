package no.nav.klage.oppgave.clients.kant

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.Utfall
import no.nav.klage.kodeverk.hjemmel.Registreringshjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class KantSaksdataInput(
    val kabalReference: UUID,
    val sakstype: Type,
    val ytelse: Ytelse,
    val trygderettenSaksnummer: String,
)

data class KantUpdateInput(
    val registreringshjemler: Set<Registreringshjemmel>,
)

data class KantAvslutningInput(
    val utfall: Utfall,
    val registreringshjemler: Set<Registreringshjemmel>,
    val avsluttetAvSaksbehandler: LocalDateTime,
    val sakITrygderettenOpprettet: LocalDate? = null,
    val mottattKlageinstans: LocalDate? = null,
    val tilknyttetEnhetId: String,
    val tilbakekreving: Boolean,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KantSaksdata(
    val id: UUID,
    val kabalReference: UUID,
    val sakstype: Type,
    val ytelse: Ytelse,
)

data class KantValidationErrors(
    val validationErrors: List<InvalidKantProperty>,
)

data class InvalidKantProperty(
    val field: String,
    val reason: String,
)
