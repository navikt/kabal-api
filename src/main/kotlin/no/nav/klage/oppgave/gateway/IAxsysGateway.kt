package no.nav.klage.oppgave.gateway

import no.nav.klage.oppgave.domain.saksbehandler.EnheterMedLovligeTemaer
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerReferanse

interface IAxsysGateway {

    fun getEnheterMedTemaerForSaksbehandler(ident: String): EnheterMedLovligeTemaer
    fun getSaksbehandlereIEnhet(enhetId: String): List<SaksbehandlerReferanse>
}