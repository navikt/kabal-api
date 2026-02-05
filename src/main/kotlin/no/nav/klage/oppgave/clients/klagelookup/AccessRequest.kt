package no.nav.klage.oppgave.clients.klagelookup

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.ytelse.Ytelse

data class AccessRequest(
    val brukerId: String,
    val navIdent: String?,
    val sak: Sak?,
) {
    data class Sak(
        val sakId: String,
        val ytelse: Ytelse,
        val fagsystem: Fagsystem,
    )
}