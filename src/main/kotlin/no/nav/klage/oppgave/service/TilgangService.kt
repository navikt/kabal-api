package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupClient
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.stereotype.Service

@Service
class TilgangService(
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val saksbehandlerService: SaksbehandlerService,
    private val klageLookupClient: KlageLookupClient,
    ) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    fun verifyInnloggetSaksbehandlersSkrivetilgang(behandling: Behandling) {
        if (behandling.ferdigstilling != null) {
            throw BehandlingAvsluttetException("Kan ikke endre avsluttet behandling")
        }
        if (behandling.feilregistrering != null) {
            throw BehandlingAvsluttetException("Kan ikke endre feilregistrert behandling")
        }

        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        if (!saksbehandlerHarSkrivetilgang(behandling, ident)) {
            throw MissingTilgangException("Kun tildelt saksbehandler kan endre behandlingen")
        }
    }

    private fun saksbehandlerHarSkrivetilgang(behandling: Behandling, ident: String): Boolean =
        ident == behandling.tildeling?.saksbehandlerident

    fun verifyInnloggetSaksbehandlersTilgangTil(
        fnr: String,
        sakId: String,
        ytelse: Ytelse,
        fagsystem: Fagsystem,
    ) {
        val access = getSaksbehandlerAccessToSak(
            fnr = fnr,
            sakId = sakId,
            ytelse = ytelse,
            fagsystem = fagsystem,
        )
        if (!access.access) {
            throw MissingTilgangException(access.reason)
        }
    }

    fun verifySaksbehandlersAccessToYtelse(saksbehandlerIdent: String, ytelse: Ytelse) {
        if (!saksbehandlerService.saksbehandlerHasAccessToYtelse(saksbehandlerIdent, ytelse)) {
            throw MissingTilgangException("Saksbehandler har ikke tilgang til ytelse $ytelse")
        }
    }

    fun verifyInnloggetSaksbehandlerIsMedunderskriverOrROLAndNotFinalized(behandling: Behandling) {
        if (behandling.ferdigstilling != null) {
            throw BehandlingAvsluttetException("Kan ikke endre avsluttet behandling")
        }
        if (behandling.feilregistrering != null) {
            throw BehandlingAvsluttetException("Kan ikke endre feilregistrert behandling")
        }
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        if (ident != behandling.medunderskriver?.saksbehandlerident && ident != behandling.rolIdent) {
            throw MissingTilgangException("Innlogget saksbehandler er ikke medunderskriver eller ROL")
        }
    }

    fun getSaksbehandlerAccessToPerson(
        fnr: String,
        navIdent: String? = null,
    ): Access {
        return klageLookupClient.getAccess(
            brukerId = fnr,
            navIdent = navIdent,
            sakId = null,
            ytelse = null,
            fagsystem = null,
        )
    }

    fun getSaksbehandlerAccessToSak(
        fnr: String,
        navIdent: String? = null,
        sakId: String,
        ytelse: Ytelse,
        fagsystem: Fagsystem,
    ): Access {
        return klageLookupClient.getAccess(
            brukerId = fnr,
            navIdent = navIdent,
            sakId = sakId,
            ytelse = ytelse,
            fagsystem = fagsystem,
        )
    }

    data class Access(
        val access: Boolean,
        val reason: String,
    )
}