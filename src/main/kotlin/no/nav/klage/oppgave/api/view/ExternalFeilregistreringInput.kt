package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.Type

data class ExternalFeilregistreringInput(
    val reason: String,
    val fagsystem: Fagsystem,
    val type: Type,
    val navIdent: String,
    val kildereferanse: String,
)
