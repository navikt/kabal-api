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
        val access = harInnloggetSaksbehandlerTilgangTil(fnr)
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

    fun harInnloggetSaksbehandlerTilgangTil(fnr: String): Access {
        return verifiserTilgangTilPersonForSaksbehandler(
            fnr = fnr,
            kanBehandleStrengtFortrolig = { innloggetSaksbehandlerService.kanBehandleStrengtFortrolig() },
            kanBehandleFortrolig = { innloggetSaksbehandlerService.kanBehandleFortrolig() },
            kanBehandleEgenAnsatt = { innloggetSaksbehandlerService.kanBehandleEgenAnsatt() },
        )
    }

    private fun verifiserTilgangTilPersonForSaksbehandler(
        fnr: String,
        kanBehandleStrengtFortrolig: () -> Boolean,
        kanBehandleFortrolig: () -> Boolean,
        kanBehandleEgenAnsatt: () -> Boolean
    ): Access {
        val useTilgangsmaskinen = false
        logger.debug("Using ${if (useTilgangsmaskinen) "Tilgangsmaskinen" else "custom implementation"} for access check.")

        val tilgangsmaskinenAccess = getTilgangsmaskinenAccess(brukerId = fnr)

        val customAccess = getOurCustomAccess(
            fnr = fnr,
            kanBehandleStrengtFortrolig = kanBehandleStrengtFortrolig,
            kanBehandleFortrolig = kanBehandleFortrolig,
            kanBehandleEgenAnsatt = kanBehandleEgenAnsatt,
        )

        if (!customAccess.access || !tilgangsmaskinenAccess.access) {
            if (!customAccess.access && !tilgangsmaskinenAccess.access) {
                teamLogger.debug("Our implementation of access checks and Tilgangsmaskinen both deny access, with these reasons: our reason='${customAccess.reason}', Tilgangsmaskinen reason='${tilgangsmaskinenAccess.reason}'.")
            }

            if (!customAccess.access && tilgangsmaskinenAccess.access) {
                logger.error("Our implementation of access checks disagrees with Tilgangsmaskinen. Check team logs.")
                teamLogger.error("Our implementation of access checks disagrees with Tilgangsmaskinen: access=$customAccess, but no forbidden response from Tilgangsmaskinen: $tilgangsmaskinenAccess.")
            } else if (customAccess.access && !tilgangsmaskinenAccess.access) {
                logger.error("Our implementation of access checks disagrees with Tilgangsmaskinen. Check team logs.")
                teamLogger.error("Our implementation of access checks disagrees with Tilgangsmaskinen: access=$customAccess, but forbidden response from Tilgangsmaskinen: $tilgangsmaskinenAccess.")
            }
        }

        return if (useTilgangsmaskinen) {
            tilgangsmaskinenAccess
        } else customAccess
    }

    private fun getTilgangsmaskinenAccess(brukerId: String): Access {
        val tilgangsmaskinenStart = System.currentTimeMillis()
        val tilgangsmaskinenErrorResponse = tilgangsmaskinenRestClient.getTilgangsmaskinenErrorResponse(brukerId = brukerId)
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

    private fun getOurCustomAccess(
        fnr: String,
        kanBehandleStrengtFortrolig: () -> Boolean,
        kanBehandleFortrolig: () -> Boolean,
        kanBehandleEgenAnsatt: () -> Boolean
    ): Access {
        val manualCheckStart = System.currentTimeMillis()
        val personInfo = personService.getPersonInfo(fnr)
        val harBeskyttelsesbehovFortrolig = personInfo.harBeskyttelsesbehovFortrolig()
        val harBeskyttelsesbehovStrengtFortrolig = personInfo.harBeskyttelsesbehovStrengtFortrolig()
        val erEgenAnsatt = egenAnsattService.erEgenAnsatt(fnr)

        val access = when {
            harBeskyttelsesbehovStrengtFortrolig && !kanBehandleStrengtFortrolig.invoke() -> {
                logger.debug("erStrengtFortrolig")
                //Merk at vi ikke sjekker egenAnsatt her, strengt fortrolig trumfer det
                Access(access = false, reason = "Ikke tilgang til adressebeskyttelse strengt fortrolig")
            }

            harBeskyttelsesbehovFortrolig && !kanBehandleFortrolig.invoke() -> {
                logger.debug("erFortrolig")
                //Merk at vi ikke sjekker egenAnsatt her, fortrolig trumfer det
                Access(access = false, reason = "Ikke tilgang til adressebeskyttelse fortrolig")
            }

            erEgenAnsatt && !(harBeskyttelsesbehovFortrolig || harBeskyttelsesbehovStrengtFortrolig) && !kanBehandleEgenAnsatt.invoke() -> {
                logger.debug("erEgenAnsatt")
                //Er kun egenAnsatt, har ikke et beskyttelsesbehov i tillegg
                Access(access = false, reason = "Ikke tilgang til egen ansatt")
            }

            else -> {
                Access(access = true, reason = "Access granted")
            }
        }
        logger.debug("Manual checks took ${System.currentTimeMillis() - manualCheckStart} ms")
        return access
    }

    data class Access(
        val access: Boolean,
        val reason: String,
    )
}
