package no.nav.klage.oppgave.clients.klagelookup

import no.nav.klage.kodeverk.AzureGroup
import no.nav.klage.oppgave.domain.person.Person
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerEnhet
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerGroups
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerPersonligInfo
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerSluttdato
import no.nav.klage.oppgave.service.TilgangService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service

@Service
class KlageLookupGateway(
    private val klageLookupClient: KlageLookupClient,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getUserInfoForGivenNavIdent(navIdent: String): SaksbehandlerPersonligInfo {
        logger.debug("Getting user info for $navIdent from KlageLookup")
        val data = klageLookupClient.getUserInfo(navIdent = navIdent)
        return data.toSaksbehandlerPersonligInfo()
    }

    fun getSluttdatoForGivenNavIdent(navIdent: String): SaksbehandlerSluttdato {
        logger.debug("Getting sluttdato for $navIdent from KlageLookup")
        val data = klageLookupClient.getUserSluttdato(navIdent = navIdent)
        return data.toSaksbehandlerSluttdato()
    }

    fun getUserInfoForNavIdentList(navIdentList: List<String>): List<SaksbehandlerPersonligInfo> {
        logger.debug("Getting user info for $navIdentList from KlageLookup")
        val data = klageLookupClient.getUserInfoBatched(navIdentList = navIdentList)
        if (data.misses.isNotEmpty()) {
            logger.warn("Did not find user info for ${data.misses} from KlageLookup")
        }
        return data.hits.map { it.toSaksbehandlerPersonligInfo() }
    }

    fun getSluttdatoForNavIdentList(navIdentList: List<String>): List<SaksbehandlerSluttdato> {
        logger.debug("Getting sluttdato for $navIdentList from KlageLookup")
        val data = klageLookupClient.getSluttdatoBatched(navIdentList = navIdentList)
        return data.toSaksbehandlerSluttdatoList()
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
        sakId: String? = null,
        ytelse: no.nav.klage.kodeverk.ytelse.Ytelse? = null,
        fagsystem: no.nav.klage.kodeverk.Fagsystem? = null,
    ): TilgangService.Access {
        return klageLookupClient.getAccess(
            brukerId = brukerId,
            navIdent = navIdent,
            sakId = sakId,
            ytelse = ytelse,
            fagsystem = fagsystem,
        )
    }

    fun getPerson(fnr: String, sak: Sak?): Person {
        return klageLookupClient.getPerson(fnr = fnr, sak = sak).toPerson()
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

    private fun SluttdatoResponse.toSaksbehandlerSluttdato(): SaksbehandlerSluttdato {
        return SaksbehandlerSluttdato(
            navIdent = this.navIdent,
            sluttdato = this.sluttdato,
        )
    }

    private fun BatchedSluttdatoResponse.toSaksbehandlerSluttdatoList(): List<SaksbehandlerSluttdato> {
        val resultList = mutableListOf<SaksbehandlerSluttdato>()
        hits.forEach {
            resultList.add(
                SaksbehandlerSluttdato(
                    navIdent = it.navIdent,
                    sluttdato = it.sluttdato,
                )
            )
        }
        misses.forEach {
            resultList.add(
                SaksbehandlerSluttdato(
                    navIdent = it,
                    sluttdato = null,
                )
            )
        }
        return resultList
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