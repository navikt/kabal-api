package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.domain.saksbehandler.Enhet
import no.nav.klage.oppgave.domain.saksbehandler.EnheterMedLovligeYtelser
import no.nav.klage.oppgave.util.TokenUtil
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class InnloggetSaksbehandlerRepository(
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val tokenUtil: TokenUtil,
    @Value("\${ROLE_GOSYS_OPPGAVE_BEHANDLER}") private val gosysSaksbehandlerRole: String,
    @Value("\${ROLE_KLAGE_SAKSBEHANDLER}") private val saksbehandlerRole: String,
    @Value("\${ROLE_KLAGE_FAGANSVARLIG}") private val fagansvarligRole: String,
    @Value("\${ROLE_KLAGE_LEDER}") private val lederRole: String,
    @Value("\${ROLE_KLAGE_MERKANTIL}") private val merkantilRole: String,
    @Value("\${ROLE_KLAGE_FORTROLIG}") private val kanBehandleFortroligRole: String,
    @Value("\${ROLE_KLAGE_STRENGT_FORTROLIG}") private val kanBehandleStrengtFortroligRole: String,
    @Value("\${ROLE_KLAGE_EGEN_ANSATT}") private val kanBehandleEgenAnsattRole: String,
    @Value("\${ROLE_ADMIN}") private val adminRole: String
) {

    fun getEnheterMedYtelserForSaksbehandler(): EnheterMedLovligeYtelser =
        saksbehandlerRepository.getEnheterMedYtelserForSaksbehandler(getInnloggetIdent())

    fun getEnheterForSaksbehandler(): List<Enhet> =
        saksbehandlerRepository.getEnheterForSaksbehandler(getInnloggetIdent())

    fun getInnloggetIdent() = tokenUtil.getIdent()

    fun erAdmin(): Boolean = tokenUtil.getRollerFromToken().hasRole(adminRole)

    fun erLeder(): Boolean = tokenUtil.getRollerFromToken().hasRole(lederRole)

    fun erFagansvarlig(): Boolean = tokenUtil.getRollerFromToken().hasRole(fagansvarligRole)

    fun erSaksbehandler(): Boolean =
        tokenUtil.getRollerFromToken().hasRole(saksbehandlerRole) || tokenUtil.getRollerFromToken()
            .hasRole(gosysSaksbehandlerRole)

    fun kanBehandleFortrolig(): Boolean = tokenUtil.getRollerFromToken().hasRole(kanBehandleFortroligRole)

    fun kanBehandleStrengtFortrolig(): Boolean =
        tokenUtil.getRollerFromToken().hasRole(kanBehandleStrengtFortroligRole)

    fun kanBehandleEgenAnsatt(): Boolean = tokenUtil.getRollerFromToken().hasRole(kanBehandleEgenAnsattRole)

    fun harTilgangTilEnhet(enhetId: String): Boolean {
        return saksbehandlerRepository.harTilgangTilEnhet(getInnloggetIdent(), enhetId)
    }

    fun harTilgangTilYtelse(ytelse: Ytelse): Boolean {
        return saksbehandlerRepository.harTilgangTilYtelse(getInnloggetIdent(), ytelse)
    }

    fun harTilgangTilEnhetOgYtelse(enhetId: String, ytelse: Ytelse): Boolean {
        return saksbehandlerRepository.harTilgangTilEnhetOgYtelse(getInnloggetIdent(), enhetId, ytelse)
    }

    private fun List<String>.hasRole(role: String) = any { it.contains(role) }
}
