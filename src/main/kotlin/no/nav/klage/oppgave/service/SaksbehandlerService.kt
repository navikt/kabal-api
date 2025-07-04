package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.nom.GetAnsattResponse
import no.nav.klage.oppgave.clients.nom.NomClient
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import no.nav.klage.oppgave.domain.saksbehandler.Enhet
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.util.getLogger
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
    @Value("\${KABAL_SVARBREVINNSTILLINGER_ROLE_ID}") private val kabalSvarbrevinnstillingerRoleId: String,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getEnhetForSaksbehandler(navIdent: String): Enhet {
        return azureGateway.getPersonligDataOmSaksbehandlerMedIdent(navIdent = navIdent).enhet
    }

    @Cacheable(CacheWithJCacheConfiguration.SAKSBEHANDLER_NAME_CACHE)
    fun getNameForIdentDefaultIfNull(navIdent: String): String {
        if (navIdent == systembrukerIdent) {
            return navIdent
        }

        return try {
            azureGateway.getPersonligDataOmSaksbehandlerMedIdent(navIdent).sammensattNavn
        } catch (_: Exception) {
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
        return ansatt
    }

    fun isROL(ident: String): Boolean = getRoleIds(ident).contains(kabalROLRoleId)

    fun isKROL(ident: String): Boolean = getRoleIds(ident).contains(kabalKROLRoleId)

    fun isKabalSvarbrevinnstillinger(ident: String): Boolean =
        getRoleIds(ident).contains(kabalSvarbrevinnstillingerRoleId)

    fun hasFortroligRole(ident: String, useCache: Boolean = false): Boolean {
        return if (useCache) {
            getNavIdentsForRoleId(fortroligRoleId).contains(ident)
        } else {
            getRoleIds(ident).contains(fortroligRoleId)
        }
    }

    fun hasStrengtFortroligRole(ident: String, useCache: Boolean = false): Boolean {
        return if (useCache) {
            getNavIdentsForRoleId(strengtFortroligRoleId).contains(ident)
        } else {
            getRoleIds(ident).contains(strengtFortroligRoleId)
        }
    }

    fun hasEgenAnsattRole(ident: String, useCache: Boolean = false): Boolean {
        return if (useCache) {
            getNavIdentsForRoleId(egenAnsattRoleId).contains(ident)
        } else {
            getRoleIds(ident).contains(egenAnsattRoleId)
        }
    }

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

    private fun getNavIdentsForRoleId(roleId: String): List<String> = try {
        azureGateway.getGroupMembersNavIdents(roleId)
    } catch (e: Exception) {
        logger.warn("Failed to retrieve navidents for role $roleId, using emptylist instead")
        emptyList()
    }

    fun hasKabalInnsynEgenEnhetRole(ident: String): Boolean =
        getRoleIds(ident).contains(kabalInnsynEgenEnhetRoleId)
}
