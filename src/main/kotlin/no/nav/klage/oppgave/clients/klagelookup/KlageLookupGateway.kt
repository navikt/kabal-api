package no.nav.klage.oppgave.clients.klagelookup

import no.nav.klage.kodeverk.AzureGroup
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerEnhet
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerGroupMemberships
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerPersonligInfo
import no.nav.klage.oppgave.service.TilgangService
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service

@Service
class KlageLookupGateway(
    private val klageLookupClient: KlageLookupClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getUserInfoForCurrentUser(): SaksbehandlerPersonligInfo {
        logger.debug("Getting user info for current user from KlageLookup")
        val data = klageLookupClient.getUserInfo(navIdent = tokenUtil.getIdent())
        return data!!.toSaksbehandlerPersonligInfo()
    }

    fun getUserInfoForGivenNavIdent(navIdent: String): SaksbehandlerPersonligInfo? {
        logger.debug("Getting user info for $navIdent from KlageLookup")
        val data = klageLookupClient.getUserInfo(navIdent = navIdent)
        return data?.toSaksbehandlerPersonligInfo()
    }

    fun getGroupMembershipsForGivenNavIdent(navIdent: String): SaksbehandlerGroupMemberships {
        logger.debug("Getting group memberships for $navIdent from KlageLookup")
        val data = klageLookupClient.getUserGroupMemberships(navIdent = navIdent)
        return data.toSaksbehandlerGroupMemberships()
    }

    fun getUsersInGroup(azureGroup: AzureGroup): List<UserResponse> {
        logger.debug("Getting users in group $azureGroup from KlageLookup")
        val data = klageLookupClient.getUsersInGroup(azureGroup = azureGroup)
        return data?.users ?: emptyList()
    }

    fun getAccess(
        /** fnr, dnr or aktorId */
        brukerId: String,
        navIdent: String? = null,
        sakId: String? = null,
        ytelse: no.nav.klage.kodeverk.ytelse.Ytelse? = null,
        fagsystem: no.nav.klage.kodeverk.Fagsystem? = null,
    ): TilgangService.Access {
        logger.debug("Getting access for user $brukerId and navIdent $navIdent from KlageLookup")
        return klageLookupClient.getAccess(
            brukerId = brukerId,
            navIdent = navIdent,
            sakId = sakId,
            ytelse = ytelse,
            fagsystem = fagsystem,
        )
    }

    fun ExtendedUserResponse.toSaksbehandlerPersonligInfo(): SaksbehandlerPersonligInfo {
        return SaksbehandlerPersonligInfo(
            navIdent = this.navIdent,
            fornavn = this.fornavn,
            etternavn = this.etternavn,
            sammensattNavn = this.sammensattNavn,
            epost = this.epost,
            enhet = SaksbehandlerEnhet(
                enhetId = this.enhet.enhetNr,
                navn = this.enhet.enhetNavn,
            )
        )
    }

    fun GroupsResponse?.toSaksbehandlerGroupMemberships(): SaksbehandlerGroupMemberships {
        return SaksbehandlerGroupMemberships(
            groups = this?.groupIds?.map { AzureGroup.of(it) } ?: emptyList(),
        )
    }
}