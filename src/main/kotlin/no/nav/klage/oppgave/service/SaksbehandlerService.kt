package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.clients.nom.GetAnsattResponse
import no.nav.klage.oppgave.clients.nom.NomClient
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import no.nav.klage.oppgave.domain.saksbehandler.Enhet
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class SaksbehandlerService(
    private val kabalInnstillingerService: KabalInnstillingerService,
    private val nomClient: NomClient,
    private val azureGateway: AzureGateway,
    @Value("\${FORTROLIG_ROLE_ID}") private val fortroligRoleId: String,
    @Value("\${STRENGT_FORTROLIG_ROLE_ID}") private val strengtFortroligRoleId: String,
    @Value("\${EGEN_ANSATT_ROLE_ID}") private val egenAnsattRoleId: String,
    @Value("\${KABAL_OPPGAVESTYRING_ALLE_ENHETER_ROLE_ID}") private val kabalOppgavestyringAlleEnheterRoleId: String,
    @Value("\${KABAL_ADMIN_ROLE_ID}") private val kabalAdminRoleId: String,
    @Value("\${KABAL_INNSYN_EGEN_ENHET_ROLE_ID}") private val kabalInnsynEgenEnhetRoleId: String,
    @Value("\${KABAL_ROL_ROLE_ID}") private val kabalROLRoleId: String,
    @Value("\${KABAL_KROL_ROLE_ID}") private val kabalKROLRoleId: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val secureLogger = getSecureLogger()
    }

    fun getEnhetForSaksbehandler(navIdent: String): Enhet {
        return azureGateway.getPersonligDataOmSaksbehandlerMedIdent(navIdent = navIdent).enhet
    }

    @Cacheable(CacheWithJCacheConfiguration.SAKSBEHANDLER_NAME_CACHE)
    fun getNameForIdentDefaultIfNull(navIdent: String): String {
        return try {
            azureGateway.getPersonligDataOmSaksbehandlerMedIdent(navIdent).sammensattNavn
        } catch (e: Exception) {
            "Ukjent navn"
        }
    }

    private fun getTildelteYtelserForSaksbehandler(navIdent: String): List<Ytelse> {
        return kabalInnstillingerService.getTildelteYtelserForSaksbehandler(navIdent)
    }

    fun saksbehandlerHasAccessToYtelse(navIdent: String, ytelse: Ytelse): Boolean {
        return hasKabalOppgavestyringAlleEnheterRole(navIdent)
                || getTildelteYtelserForSaksbehandler(navIdent).contains(ytelse)
    }

    fun getAnsattInfoFromNom(navIdent: String): GetAnsattResponse {
        val ansatt = nomClient.getAnsatt(navIdent)
        secureLogger.debug(
            ansatt.toString()
        )
        return ansatt
    }

    fun isROL(ident: String): Boolean = getRoleIds(ident).contains(kabalROLRoleId)

    fun isKROL(ident: String): Boolean = getRoleIds(ident).contains(kabalKROLRoleId)

    fun hasFortroligRole(ident: String): Boolean = getRoleIds(ident).contains(fortroligRoleId)

    fun hasStrengtFortroligRole(ident: String): Boolean =
        getRoleIds(ident).contains(strengtFortroligRoleId)

    fun hasEgenAnsattRole(ident: String): Boolean = getRoleIds(ident).contains(egenAnsattRoleId)

    fun hasKabalOppgavestyringAlleEnheterRole(ident: String): Boolean =
        getRoleIds(ident).contains(kabalOppgavestyringAlleEnheterRoleId)

    fun hasKabalAdminRole(ident: String): Boolean =
        getRoleIds(ident).contains(kabalAdminRoleId)

    private fun getRoleIds(ident: String): List<String> = try {
        azureGateway.getRoleIds(ident)
    } catch (e: Exception) {
        logger.warn("Failed to retrieve roller for navident $ident, using emptylist instead")
        emptyList()
    }

    fun hasKabalInnsynEgenEnhetRole(ident: String): Boolean =
        getRoleIds(ident).contains(kabalInnsynEgenEnhetRoleId)
}
