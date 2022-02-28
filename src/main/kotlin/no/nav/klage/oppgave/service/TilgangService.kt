package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.Behandling
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.repositories.SaksbehandlerRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service

@Service
class TilgangService(
    private val pdlFacade: PdlFacade,
    private val egenAnsattService: EgenAnsattService,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securelogger = getSecureLogger()
    }

    fun verifyInnloggetSaksbehandlersSkrivetilgang(klagebehandling: Klagebehandling) {
        if (klagebehandling.currentDelbehandling().avsluttetAvSaksbehandler != null || klagebehandling.currentDelbehandling().avsluttet != null) {
            throw BehandlingAvsluttetException("Kan ikke endre avsluttet klagebehandling")
        }
        val ident = innloggetSaksbehandlerRepository.getInnloggetIdent()
        if (!saksbehandlerHarSkrivetilgang(klagebehandling, ident)) {
            throw MissingTilgangException("Kun tildelt saksbehandler kan endre klagebehandlingen")
        }
    }

    fun verifyInnloggetSaksbehandlersSkrivetilgang(behandling: Behandling) {
        if (behandling.currentDelbehandling().avsluttetAvSaksbehandler != null || behandling.currentDelbehandling().avsluttet != null) {
            throw BehandlingAvsluttetException("Kan ikke endre avsluttet klagebehandling")
        }
        val ident = innloggetSaksbehandlerRepository.getInnloggetIdent()
        if (!saksbehandlerHarSkrivetilgang(behandling, ident)) {
            throw MissingTilgangException("Kun tildelt saksbehandler kan endre klagebehandlingen")
        }
    }

    private fun saksbehandlerHarSkrivetilgang(klagebehandling: Klagebehandling, ident: String): Boolean =
        ident == klagebehandling.tildeling?.saksbehandlerident

    private fun saksbehandlerHarSkrivetilgang(behandling: Behandling, ident: String): Boolean =
        ident == behandling.tildeling?.saksbehandlerident

    fun verifySystembrukersSkrivetilgang(behandling: Behandling) {
        if (behandling.currentDelbehandling().avsluttet != null) {
            throw BehandlingAvsluttetException("Kan ikke endre avsluttet behandling")
        }
    }

    fun verifyInnloggetSaksbehandlersTilgangTil(fnr: String) {
        if (!harInnloggetSaksbehandlerTilgangTil(fnr)) {
            throw MissingTilgangException("Saksbehandler har ikke tilgang til denne brukeren")
        }
    }

    fun verifySaksbehandlersTilgangTilEnhetOgYtelse(saksbehandlerIdent: String, enhetId: String, ytelse: Ytelse) {
        if (!saksbehandlerRepository.harTilgangTilEnhetOgYtelse(saksbehandlerIdent, enhetId, ytelse)) {
            throw MissingTilgangException("Saksbehandler har ikke tilgang til ytelse $ytelse i enhet $enhetId")
        }
    }

    //Trenger vi denne?
    fun verifyInnloggetSaksbehandlersTilgangTilYtelse(ytelse: Ytelse) {
        if (!innloggetSaksbehandlerRepository.harTilgangTilYtelse(ytelse)) {
            throw MissingTilgangException("Saksbehandler har ikke tilgang til ytelse $ytelse")
        }
    }

    fun verifyInnloggetSaksbehandlerErMedunderskriver(behandling: Behandling) {
        val ident = innloggetSaksbehandlerRepository.getInnloggetIdent()
        if (ident != behandling.currentDelbehandling().medunderskriver?.saksbehandlerident) {
            throw MissingTilgangException("Innlogget saksbehandler er ikke medunderskriver")
        }
    }

    fun harInnloggetSaksbehandlerTilgangTil(fnr: String): Boolean {
        val ident = innloggetSaksbehandlerRepository.getInnloggetIdent()
        return verifiserTilgangTilPersonForSaksbehandler(
            fnr = fnr,
            ident = ident,
            kanBehandleStrengtFortrolig = { innloggetSaksbehandlerRepository.kanBehandleStrengtFortrolig() },
            kanBehandleFortrolig = { innloggetSaksbehandlerRepository.kanBehandleFortrolig() },
            kanBehandleEgenAnsatt = { innloggetSaksbehandlerRepository.kanBehandleEgenAnsatt() },
        )
    }

    fun harSaksbehandlerTilgangTil(ident: String, fnr: String): Boolean {
        return verifiserTilgangTilPersonForSaksbehandler(
            fnr = fnr,
            ident = ident,
            kanBehandleStrengtFortrolig = { saksbehandlerRepository.kanBehandleStrengtFortrolig(ident) },
            kanBehandleFortrolig = { saksbehandlerRepository.kanBehandleFortrolig(ident) },
            kanBehandleEgenAnsatt = { saksbehandlerRepository.kanBehandleEgenAnsatt(ident) },
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
