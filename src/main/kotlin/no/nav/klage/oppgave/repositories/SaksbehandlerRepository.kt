package no.nav.klage.oppgave.repositories

import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class SaksbehandlerRepository(
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

        val saksbehandlerNameCache = mutableMapOf<String, String>()

        const val MAX_AMOUNT_IDENTS_IN_GRAPH_QUERY = 15
    }

    fun getNamesForSaksbehandlere(identer: Set<String>): Map<String, String> {
        logger.debug("Fetching names for saksbehandlere from Microsoft Graph")

        val identerNotInCache = identer.toMutableSet()
        identerNotInCache -= saksbehandlerNameCache.keys
        logger.debug("Only fetching identer not in cache: {}", identerNotInCache)

        val chunkedList = identerNotInCache.chunked(MAX_AMOUNT_IDENTS_IN_GRAPH_QUERY)

        val measuredTimeMillis = measureTimeMillis {
            saksbehandlerNameCache += azureGateway.getAllDisplayNames(chunkedList)
        }
        logger.debug("It took {} millis to fetch all names", measuredTimeMillis)

        return saksbehandlerNameCache
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

    fun getNameForSaksbehandler(navIdent: String): String {
        return azureGateway.getPersonligDataOmSaksbehandlerMedIdent(navIdent).sammensattNavn
    }

    fun hasKabalInnsynEgenEnhetRole(ident: String): Boolean =
        getRoleIds(ident).contains(kabalInnsynEgenEnhetRoleId)
}