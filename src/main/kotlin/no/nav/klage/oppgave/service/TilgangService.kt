package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.tilgangsmaskinen.TilgangsmaskinenRestClient
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.stereotype.Service

@Service
class TilgangService(
    private val personService: PersonService,
    private val egenAnsattService: EgenAnsattService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val saksbehandlerService: SaksbehandlerService,
    private val tilgangsmaskinenRestClient: TilgangsmaskinenRestClient,
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

    fun verifyInnloggetSaksbehandlersTilgangTil(fnr: String) {
        val access = hasSaksbehandlerAccessTo(fnr)
        if (!access.access) {
            throw MissingTilgangException(access.reason ?: "Saksbehandler har ikke tilgang til denne brukeren")
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

    fun hasSaksbehandlerAccessTo(
        fnr: String,
        navIdent: String? = null,
    ): Access {
        return getTilgangsmaskinenAccess(
            brukerId = fnr,
            navIdent = navIdent,
        )
    }

    private fun getTilgangsmaskinenAccess(
        brukerId: String,
        navIdent: String?,
    ): Access {
        val tilgangsmaskinenStart = System.currentTimeMillis()

        val tilgangsmaskinenErrorResponse = tilgangsmaskinenRestClient.getTilgangsmaskinenErrorResponse(brukerId = brukerId, navIdent = navIdent)

        val tilgangsmaskinenAccess = if (tilgangsmaskinenErrorResponse != null) {
            Access(
                access = false,
                reason = tilgangsmaskinenErrorResponse.begrunnelse
            )
        } else {
            Access(
                access = true,
                reason = "Access granted",
            )
        }
        logger.debug("Tilgangsmaskinen took ${System.currentTimeMillis() - tilgangsmaskinenStart} ms")
        return tilgangsmaskinenAccess
    }

    data class Access(
        val access: Boolean,
        val reason: String,
    )
}
