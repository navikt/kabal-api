package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.AzureGroup
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.clients.nom.GetAnsattResponse
import no.nav.klage.oppgave.clients.nom.NomClient
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerEnhet
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerGroupMemberships
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable

import org.springframework.stereotype.Service

@Service
class SaksbehandlerService(
    private val kabalInnstillingerService: KabalInnstillingerService,
    private val nomClient: NomClient,
    private val klageLookupGateway: KlageLookupGateway,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getEnhetForSaksbehandler(navIdent: String): SaksbehandlerEnhet? {
        return klageLookupGateway.getUserInfoForGivenNavIdent(navIdent = navIdent)?.enhet
    }

    @Cacheable(CacheWithJCacheConfiguration.SAKSBEHANDLER_NAME_CACHE)
    fun getNameForIdentDefaultIfNull(navIdent: String): String {
        if (navIdent == systembrukerIdent) {
            return navIdent
        }

        return try {
            klageLookupGateway.getUserInfoForGivenNavIdent(navIdent = navIdent)?.sammensattNavn ?: "Ukjent navn"
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

    fun isSaksbehandler(ident: String): Boolean =
        getSaksbehandlerGroupMemberships(ident).groups.contains(AzureGroup.KABAL_SAKSBEHANDLING)

    fun isROL(ident: String): Boolean =
        getSaksbehandlerGroupMemberships(ident).groups.contains(AzureGroup.KABAL_ROL)

    fun isKROL(ident: String): Boolean =
        getSaksbehandlerGroupMemberships(ident).groups.contains(AzureGroup.KABAL_KROL)

    fun isKabalSvarbrevinnstillinger(ident: String): Boolean =
        getSaksbehandlerGroupMemberships(ident).groups.contains(AzureGroup.KABAL_SVARBREVINNSTILLINGER)

    fun hasFortroligRole(ident: String): Boolean =
        getSaksbehandlerGroupMemberships(ident).groups.contains(AzureGroup.FORTROLIG)

    fun hasEgenAnsattRole(ident: String): Boolean =
        getSaksbehandlerGroupMemberships(ident).groups.contains(AzureGroup.EGEN_ANSATT)

    fun hasKabalOppgavestyringAlleEnheterRole(ident: String): Boolean =
        getSaksbehandlerGroupMemberships(ident).groups.contains(AzureGroup.KABAL_OPPGAVESTYRING_ALLE_ENHETER)

    fun hasKabalAdminRole(ident: String): Boolean =
        getSaksbehandlerGroupMemberships(ident).groups.contains(AzureGroup.KABAL_ADMIN)

    fun hasKabalInnsynEgenEnhetRole(ident: String): Boolean =
        getSaksbehandlerGroupMemberships(ident).groups.contains(AzureGroup.KABAL_INNSYN_EGEN_ENHET)

    private fun getSaksbehandlerGroupMemberships(navIdent: String): SaksbehandlerGroupMemberships {
        return try {
            klageLookupGateway.getGroupMembershipsForGivenNavIdent(navIdent)
        } catch (e: Exception) {
            logger.warn("Failed to retrieve group memberships for navident $navIdent, using emptylist instead. Exception: $e")
            SaksbehandlerGroupMemberships(emptyList())
        }
    }
}
