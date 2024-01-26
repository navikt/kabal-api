package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.util.TokenUtil
import org.springframework.stereotype.Service

@Service
class InnloggetSaksbehandlerService(
    private val saksbehandlerService: SaksbehandlerService,
    private val tokenUtil: TokenUtil,
) {

    fun getInnloggetIdent() = tokenUtil.getIdent()

    fun isRol(): Boolean = saksbehandlerService.isROL(tokenUtil.getIdent())

    fun isKabalAdmin(): Boolean = saksbehandlerService.hasKabalAdminRole(tokenUtil.getIdent())

    fun isKabalOppgavestyringAlleEnheter(): Boolean = saksbehandlerService.hasKabalOppgavestyringAlleEnheterRole(tokenUtil.getIdent())

    fun kanBehandleFortrolig(): Boolean = saksbehandlerService.hasFortroligRole(tokenUtil.getIdent())

    fun kanBehandleStrengtFortrolig(): Boolean =
        saksbehandlerService.hasStrengtFortroligRole(tokenUtil.getIdent())

    fun kanBehandleEgenAnsatt(): Boolean =
        saksbehandlerService.hasEgenAnsattRole(tokenUtil.getIdent())

    fun hasKabalInnsynEgenEnhetRole(): Boolean =
        saksbehandlerService.hasKabalInnsynEgenEnhetRole(tokenUtil.getIdent())
}
