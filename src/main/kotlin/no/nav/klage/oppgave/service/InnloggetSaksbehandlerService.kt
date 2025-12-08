package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.util.TokenUtil
import org.springframework.stereotype.Service

@Service
class InnloggetSaksbehandlerService(
    private val saksbehandlerService: SaksbehandlerService,
    private val tokenUtil: TokenUtil,
) {

    fun getInnloggetIdent() = tokenUtil.getIdent()

    fun isKabalSvarbrevinnstillinger(): Boolean = saksbehandlerService.isKabalSvarbrevinnstillinger(tokenUtil.getIdent())

    fun isROL(): Boolean = saksbehandlerService.isROL(tokenUtil.getIdent())

    fun isKabalAdmin(): Boolean = saksbehandlerService.hasKabalAdminRole(tokenUtil.getIdent())

    fun isKabalOppgavestyringAlleEnheter(): Boolean = saksbehandlerService.hasKabalOppgavestyringAlleEnheterRole(tokenUtil.getIdent())

    fun hasKabalInnsynEgenEnhetRole(): Boolean =
        saksbehandlerService.hasKabalInnsynEgenEnhetRole(tokenUtil.getIdent())
}
