package no.nav.klage.oppgave.clients.azure

import no.nav.klage.oppgave.domain.saksbehandler.Enhet
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerIdent
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerPersonligInfo
import no.nav.klage.oppgave.domain.saksbehandler.SaksbehandlerRolle
import no.nav.klage.oppgave.exceptions.EnhetNotFoundForSaksbehandlerException
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getSecureLogger
import org.springframework.stereotype.Service
import no.nav.klage.kodeverk.Enhet as KodeverkEnhet

@Service
class DefaultAzureGateway(private val microsoftGraphClient: MicrosoftGraphClient) : AzureGateway {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val securelogger = getSecureLogger()
    }

    override fun getRoleIds(ident: String): List<String> {
        return getRollerForSaksbehandlerMedIdent(ident).map { it.id }
    }

    override fun getAnsatteIEnhet(enhetId: String): List<SaksbehandlerIdent> {
        return microsoftGraphClient.getEnhetensAnsattesNavIdents(enhetId).map { SaksbehandlerIdent(it) }
    }

    override fun getGroupMembersNavIdents(groupid: String): List<String> =
        try {
            microsoftGraphClient.getGroupMembersNavIdents(groupid)
        } catch (e: Exception) {
            logger.error("Failed to call getGroupMembersNavIdents", e)
            throw e
        }

    override fun getAllDisplayNames(idents: List<List<String>>): Map<String, String> =
        try {
            microsoftGraphClient.getAllDisplayNames(idents)
        } catch (e: Exception) {
            logger.error("Failed to call getAllDisplayNames", e)
            throw e
        }

    override fun getPersonligDataOmSaksbehandlerMedIdent(navIdent: String): SaksbehandlerPersonligInfo {
        val data = try {
            microsoftGraphClient.getSaksbehandler(navIdent)
        } catch (e: Exception) {
            logger.error("Failed to call getSaksbehandler for navident $navIdent", e)
            throw e
        }
        return SaksbehandlerPersonligInfo(
            navIdent = data.onPremisesSamAccountName,
            azureId = data.id,
            fornavn = data.givenName,
            etternavn = data.surname,
            sammensattNavn = data.displayName,
            epost = data.mail,
            enhet = mapToEnhet(data.streetAddress),
        )
    }

    override fun getDataOmInnloggetSaksbehandler(): SaksbehandlerPersonligInfo {
        val data = try {
            microsoftGraphClient.getInnloggetSaksbehandler()
        } catch (e: Exception) {
            logger.error("Failed to call getInnloggetSaksbehandler", e)
            throw e
        }
        return SaksbehandlerPersonligInfo(
            navIdent = data.onPremisesSamAccountName,
            azureId = data.id,
            fornavn = data.givenName,
            etternavn = data.surname,
            sammensattNavn = data.displayName,
            epost = data.mail,
            enhet = mapToEnhet(data.streetAddress),
        )
    }

    override fun getRollerForSaksbehandlerMedIdent(navIdent: String): List<SaksbehandlerRolle> =
        try {
            logger.info("Finding roles for ident $navIdent")
            microsoftGraphClient.getSaksbehandlersGroups(navIdent)
                .map { SaksbehandlerRolle(it.id, it.displayName ?: it.onPremisesSamAccountName ?: it.id) }
        } catch (e: Exception) {
            logger.error("Failed to call getSaksbehandlersGroups for navident $navIdent", e)
            throw e
        }

    override fun getRollerForInnloggetSaksbehandler(): List<SaksbehandlerRolle> =
        try {
            microsoftGraphClient.getInnloggetSaksbehandlersGroups()
                .map { SaksbehandlerRolle(it.id, it.displayName ?: it.onPremisesSamAccountName ?: it.id) }
        } catch (e: Exception) {
            logger.error("Failed to call getInnloggetSaksbehandlersGroups", e)
            throw e
        }

    private fun mapToEnhet(enhetNr: String): Enhet =
        KodeverkEnhet.entries.find { it.navn == enhetNr }
            ?.let { Enhet(it.navn, it.beskrivelse) }
            ?: throw EnhetNotFoundForSaksbehandlerException("Enhet ikke funnet med enhetNr $enhetNr")

}