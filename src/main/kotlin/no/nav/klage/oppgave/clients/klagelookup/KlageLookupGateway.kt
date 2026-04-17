package no.nav.klage.oppgave.clients.klagelookup

import no.nav.klage.kodeverk.AzureGroup
import no.nav.klage.oppgave.domain.person.Person
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerEnhet
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerGroups
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerPersonligInfo
import no.nav.klage.oppgave.service.TilgangService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.stereotype.Service

@Service
class KlageLookupGateway(
    private val klageLookupClient: KlageLookupClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
    }

    fun getUserInfoForGivenNavIdent(navIdent: String): SaksbehandlerPersonligInfo {
        logger.debug("Getting user info for $navIdent from KlageLookup")
        val data = klageLookupClient.getUserInfo(navIdent = navIdent)
        return data.toSaksbehandlerPersonligInfo()
    }

    fun getGroupsForGivenNavIdent(navIdent: String): SaksbehandlerGroups {
        logger.debug("Getting group memberships for $navIdent from KlageLookup")
        val data = klageLookupClient.getUserGroups(navIdent = navIdent)
        return data.toSaksbehandlerGroups()
    }

    fun getUsersInGroup(azureGroup: AzureGroup): List<UserResponse> {
        logger.debug("Getting users in group $azureGroup from KlageLookup")
        val data = klageLookupClient.getUsersInGroup(azureGroup = azureGroup)
        return data.users
    }

    fun getAccess(
        /** fnr, dnr or aktorId */
        brukerId: String,
        navIdent: String? = null,
    ): TilgangService.Access {
        return klageLookupClient.getAccess(
            brukerId = brukerId,
            navIdent = navIdent,
        )
    }

    fun getPersongalleri(sak: Sak): List<String> {
        return klageLookupClient.getPersongalleri(sak = sak).foedselsnummerList
    }

    fun getPerson(fnr: String): Person {
        return klageLookupClient.getPerson(fnr = fnr).toPerson()
    }

    fun getPersonBulk(fnrList: List<String>): List<Person> {
        val response = klageLookupClient.getPersonBulk(fnrList = fnrList)
        if (response.misses.isNotEmpty()) {
            logger.warn("PDL did not return data for ${response.misses.size} of ${fnrList.size} requested fnr. See team-logs for details.")
            teamLogger.warn("PDL did not return data for the following fnr: ${response.misses.joinToString(", ")}")
        }
        return response.hits.map { it.toPerson() }
    }

    fun getFoedselsnummerFromIdent(ident: String): String {
        return klageLookupClient.getFoedselsnummerFromIdent(ident = ident)
    }

    fun getAktoerIdFromIdent(ident: String): String {
        return klageLookupClient.getAktoerIdFromIdent(ident = ident)
    }

    private fun ExtendedUserResponse.toSaksbehandlerPersonligInfo(): SaksbehandlerPersonligInfo {
        return SaksbehandlerPersonligInfo(
            navIdent = this.navIdent,
            fornavn = this.fornavn,
            etternavn = this.etternavn,
            sammensattNavn = this.sammensattNavn,
            enhet = SaksbehandlerEnhet(
                enhetId = this.enhet.enhetNr,
                navn = this.enhet.enhetNavn,
            )
        )
    }

    private fun GroupsResponse.toSaksbehandlerGroups(): SaksbehandlerGroups {
        return SaksbehandlerGroups(
            groups = this.groupIds.map { AzureGroup.of(it) }
        )
    }

    private fun PersonResponse.toPerson(): Person {
        return Person(
            foedselsnr = foedselsnr,
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn,
            sammensattNavn = sammensattNavn,
            kjoenn = kjoenn,
            doed = doed,
            strengtFortrolig = strengtFortrolig,
            strengtFortroligUtland = strengtFortroligUtland,
            fortrolig = fortrolig,
            egenAnsatt = egenAnsatt,
            vergemaalEllerFremtidsfullmakt = vergemaalEllerFremtidsfullmakt,
            sikkerhetstiltak = sikkerhetstiltak?.toSikkerhetstiltak(),
            protectedFamilyMembers = protectedFamilyMembers.map { it.toFamilyMember() },
        )
    }

    private fun PersonResponse.SikkerhetstiltakResponse.toSikkerhetstiltak(): Person.Sikkerhetstiltak {
        return Person.Sikkerhetstiltak(
            tiltakstype = Person.Sikkerhetstiltak.Tiltakstype.valueOf(tiltakstype),
            beskrivelse = beskrivelse,
            gyldigFraOgMed = gyldigFraOgMed,
            gyldigTilOgMed = gyldigTilOgMed,
        )
    }

    private fun PersonResponse.ProtectedFamilyMemberResponse.toFamilyMember(): Person.ProtectedFamilyMember {
        return Person.ProtectedFamilyMember(
            foedselsnr = foedselsnr,
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn,
            sammensattNavn = sammensattNavn,
            kjoenn = kjoenn,
            doed = doed,
            strengtFortrolig = strengtFortrolig,
            strengtFortroligUtland = strengtFortroligUtland,
            fortrolig = fortrolig,
            egenAnsatt = egenAnsatt,
        )
    }
}