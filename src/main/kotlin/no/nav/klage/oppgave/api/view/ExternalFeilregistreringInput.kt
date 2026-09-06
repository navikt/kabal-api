package no.nav.klage.oppgave.api.view

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.oppgave.domain.kafka.ExternalType

data class ExternalFeilregistreringInput(
    val reason: String,
    val fagsystem: Fagsystem,
    val type: ExternalType,
    val navIdent: String,
    val kildereferanse: String,
)
