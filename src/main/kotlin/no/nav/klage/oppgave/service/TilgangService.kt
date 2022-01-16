package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.exceptions.KlagebehandlingAvsluttetException
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
        if (klagebehandling.avsluttetAvSaksbehandler != null || klagebehandling.avsluttet != null) {
            throw KlagebehandlingAvsluttetException("Kan ikke endre avsluttet klagebehandling")
        }
        val ident = innloggetSaksbehandlerRepository.getInnloggetIdent()
        if (!saksbehandlerHarSkrivetilgang(klagebehandling, ident)) {
            throw MissingTilgangException("Kun tildelt saksbehandler kan endre klagebehandlingen")
        }
    }

    private fun saksbehandlerHarSkrivetilgang(klagebehandling: Klagebehandling, ident: String): Boolean =
        ident == klagebehandling.tildeling?.saksbehandlerident

    fun verifySystembrukersSkrivetilgang(klagebehandling: Klagebehandling) {
        if (klagebehandling.avsluttet != null) {
            throw KlagebehandlingAvsluttetException("Kan ikke endre avsluttet klagebehandling")
        }
    }

    fun verifyInnloggetSaksbehandlersTilgangTil(fnr: String) {
        if (!harInnloggetSaksbehandlerTilgangTil(fnr)) {
            throw MissingTilgangException("Saksbehandler har ikke tilgang til denne brukeren")
        }
    }

    fun verifyInnloggetSaksbehandlersTilgangTilEnhet(enhetId: String) {
        //TODO: Burde man sjekket tilgang til EnhetOgYtelse, ikke bare enhet?
        if (!innloggetSaksbehandlerRepository.harTilgangTilEnhet(enhetId)) {
            throw MissingTilgangException("Saksbehandler har ikke tilgang til enhet $enhetId")
        }
    }

    fun verifySaksbehandlersTilgangTilEnhetOgYtelse(saksbehandlerIdent: String, enhetId: String, ytelse: Ytelse) {
        if (!saksbehandlerRepository.harTilgangTilEnhetOgYtelse(saksbehandlerIdent, enhetId, ytelse)) {
            throw MissingTilgangException("Saksbehandler har ikke tilgang til ytelse $ytelse i enhet $enhetId")
        }
    }

    fun verifyInnloggetSaksbehandlersTilgangTilYtelse(ytelse: Ytelse) {
        if (!innloggetSaksbehandlerRepository.harTilgangTilYtelse(ytelse)) {
            throw MissingTilgangException("Saksbehandler har ikke tilgang til ytelse $ytelse")
        }
    }

    fun verifyInnloggetSaksbehandlerErMedunderskriver(klagebehandling: Klagebehandling) {
        val ident = innloggetSaksbehandlerRepository.getInnloggetIdent()
        if (ident != klagebehandling.delbehandlinger.first().medunderskriver?.saksbehandlerident) {
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
