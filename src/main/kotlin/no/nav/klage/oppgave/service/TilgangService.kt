package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Fagsystem
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.exceptions.BehandlingAvsluttetException
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.repositories.SakPersongalleriRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.stereotype.Service

@Service
class TilgangService(
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val saksbehandlerService: SaksbehandlerService,
    private val klageLookupGateway: KlageLookupGateway,
    private val sakPersongalleriRepository: SakPersongalleriRepository,
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

    fun verifyLoggedInUsersAccessToPerson(
        fnr: String,
    ) {
        val access = getSaksbehandlerAccessToPerson(
            fnr = fnr,
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
        return klageLookupGateway.getAccess(
            brukerId = fnr,
            navIdent = navIdent,
        )
    }

    fun getPersongalleriToCheckForBehandling(behandling: Behandling): List<String> {
        if (behandling.fagsystem == Fagsystem.FS36) {
            val persongalleriEntries = sakPersongalleriRepository.findByFagsystemAndFagsakId(
                fagsystem = behandling.fagsystem,
                fagsakId = behandling.fagsakId,
            )
            if (persongalleriEntries.isNotEmpty()) {
                return persongalleriEntries
                    .map { it.foedselsnummer }
                    .filter { it != behandling.sakenGjelder.partId.value }
            }
        }
        return if (behandling.sakenGjelder.erPerson()) {
            listOf(behandling.sakenGjelder.partId.value)
        } else {
            emptyList()
        }
    }

    fun verifyLoggedInUsersAccessToPersongalleriInBehandling(behandling: Behandling) {
        getPersongalleriToCheckForBehandling(behandling).forEach { fnr ->
            verifyLoggedInUsersAccessToPerson(fnr = fnr)
        }
    }

    fun getSaksbehandlerAccessToBehandling(
        behandling: Behandling,
        navIdent: String? = null,
    ): Access {
        getPersongalleriToCheckForBehandling(behandling).forEach { fnr ->
            val access = getSaksbehandlerAccessToPerson(fnr = fnr, navIdent = navIdent)
            if (!access.access) {
                return access
            }
        }
        return Access(access = true, reason = "")
    }

    data class Access(
        val access: Boolean,
        val reason: String,
    )
}