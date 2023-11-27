package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.klage.Behandling
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service

@Service
class TilgangService(
    private val pdlFacade: PdlFacade,
    private val egenAnsattService: EgenAnsattService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val saksbehandlerService: SaksbehandlerService,

    ) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securelogger = getSecureLogger()
    }

    //TODO: Denne brukes bare i tester, rydd opp ved anledning.
    fun verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling: Klagebehandling) {
        if (klagebehandling.avsluttetAvSaksbehandler != null || klagebehandling.avsluttet != null) {
            throw BehandlingAvsluttetException("Kan ikke endre avsluttet klagebehandling")
        }
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        if (!saksbehandlerHarSkrivetilgang(klagebehandling, ident)) {
            throw MissingTilgangException("Kun tildelt saksbehandler kan endre klagebehandlingen")
        }
    }

    fun verifyInnloggetSaksbehandlersSkrivetilgang(behandling: Behandling) {
        if (behandling.avsluttetAvSaksbehandler != null ||
            behandling.avsluttet != null
        ) {
            throw BehandlingAvsluttetException("Kan ikke endre avsluttet behandling")
        }
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        if (!saksbehandlerHarSkrivetilgang(behandling, ident)) {
            throw MissingTilgangException("Kun tildelt saksbehandler kan endre behandlingen")
        }
    }

    private fun saksbehandlerHarSkrivetilgang(klagebehandling: Klagebehandling, ident: String): Boolean =
        ident == klagebehandling.tildeling?.saksbehandlerident

    private fun saksbehandlerHarSkrivetilgang(behandling: Behandling, ident: String): Boolean =
        ident == behandling.tildeling?.saksbehandlerident

    fun checkIfBehandlingIsAvsluttet(behandling: Behandling) {
        if (behandling.avsluttet != null) {
            throw BehandlingAvsluttetException("Kan ikke endre avsluttet behandling")
        }
    }

    fun verifyInnloggetSaksbehandlersTilgangTil(fnr: String) {
        if (!harInnloggetSaksbehandlerTilgangTil(fnr)) {
            throw MissingTilgangException("Saksbehandler har ikke tilgang til denne brukeren")
        }
    }

    fun verifySaksbehandlersAccessToYtelse(saksbehandlerIdent: String, ytelse: Ytelse) {
        if (!saksbehandlerService.saksbehandlerHasAccessToYtelse(saksbehandlerIdent, ytelse)) {
            throw MissingTilgangException("Saksbehandler har ikke tilgang til ytelse $ytelse")
        }
    }

    fun verifyInnloggetSaksbehandlerIsMedunderskriverOrROLAndNotFinalized(behandling: Behandling) {
        if (behandling.avsluttetAvSaksbehandler != null || behandling.avsluttet != null) {
            throw BehandlingAvsluttetException("Kan ikke endre avsluttet klagebehandling")
        }
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        if (ident != behandling.medunderskriver?.saksbehandlerident && ident != behandling.rolIdent) {
            throw MissingTilgangException("Innlogget saksbehandler er ikke medunderskriver eller ROL")
        }
    }

    fun harInnloggetSaksbehandlerTilgangTil(fnr: String): Boolean {
        val ident = innloggetSaksbehandlerService.getInnloggetIdent()
        return verifiserTilgangTilPersonForSaksbehandler(
            fnr = fnr,
            ident = ident,
            kanBehandleStrengtFortrolig = { innloggetSaksbehandlerService.kanBehandleStrengtFortrolig() },
            kanBehandleFortrolig = { innloggetSaksbehandlerService.kanBehandleFortrolig() },
            kanBehandleEgenAnsatt = { innloggetSaksbehandlerService.kanBehandleEgenAnsatt() },
        )
    }

    private fun verifiserTilgangTilPersonForSaksbehandler(
        fnr: String,
        ident: String,
        kanBehandleStrengtFortrolig: () -> Boolean,
        kanBehandleFortrolig: () -> Boolean,
        kanBehandleEgenAnsatt: () -> Boolean
    ): Boolean {
        val personInfo = pdlFacade.getPersonInfo(fnr)
        val harBeskyttelsesbehovFortrolig = personInfo.harBeskyttelsesbehovFortrolig()
        val harBeskyttelsesbehovStrengtFortrolig = personInfo.harBeskyttelsesbehovStrengtFortrolig()
        val erEgenAnsatt = egenAnsattService.erEgenAnsatt(fnr)

        if (harBeskyttelsesbehovStrengtFortrolig) {
            securelogger.info("erStrengtFortrolig")
            //Merk at vi ikke sjekker egenAnsatt her, strengt fortrolig trumfer det
            if (kanBehandleStrengtFortrolig.invoke()) {
                securelogger.info("Access granted to strengt fortrolig for $ident")
            } else {
                securelogger.info("Access denied to strengt fortrolig for $ident")
                return false
            }
        }
        if (harBeskyttelsesbehovFortrolig) {
            securelogger.info("erFortrolig")
            //Merk at vi ikke sjekker egenAnsatt her, fortrolig trumfer det
            if (kanBehandleFortrolig.invoke()) {
                securelogger.info("Access granted to fortrolig for $ident")
            } else {
                securelogger.info("Access denied to fortrolig for $ident")
                return false
            }
        }
        if (erEgenAnsatt && !(harBeskyttelsesbehovFortrolig || harBeskyttelsesbehovStrengtFortrolig)) {
            securelogger.info("erEgenAnsatt")
            //Er kun egenAnsatt, har ikke et beskyttelsesbehov i tillegg
            if (kanBehandleEgenAnsatt.invoke()) {
                securelogger.info("Access granted to egen ansatt for $ident")
            } else {
                securelogger.info("Access denied to egen ansatt for $ident")
                return false
            }
        }
        return true
    }
}
