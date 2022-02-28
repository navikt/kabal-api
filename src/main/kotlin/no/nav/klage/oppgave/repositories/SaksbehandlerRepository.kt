package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.klageenhetTilYtelser
import no.nav.klage.oppgave.domain.saksbehandler.Enhet
import no.nav.klage.oppgave.domain.saksbehandler.EnhetMedLovligeYtelser
import no.nav.klage.oppgave.domain.saksbehandler.EnheterMedLovligeYtelser
import no.nav.klage.oppgave.gateway.AxsysGateway
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.util.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.system.measureTimeMillis

@Service
class SaksbehandlerRepository(
    private val azureGateway: AzureGateway,
    private val axsysGateway: AxsysGateway,
    @Value("\${ROLE_KLAGE_SAKSBEHANDLER}") private val saksbehandlerRole: String,
    @Value("\${ROLE_KLAGE_FAGANSVARLIG}") private val fagansvarligRole: String,
    @Value("\${ROLE_KLAGE_LEDER}") private val lederRole: String,
    @Value("\${ROLE_KLAGE_MERKANTIL}") private val merkantilRole: String,
    @Value("\${ROLE_KLAGE_FORTROLIG}") private val kanBehandleFortroligRole: String,
    @Value("\${ROLE_KLAGE_STRENGT_FORTROLIG}") private val kanBehandleStrengtFortroligRole: String,
    @Value("\${ROLE_KLAGE_EGEN_ANSATT}") private val kanBehandleEgenAnsattRole: String
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)

        val saksbehandlerNameCache = mutableMapOf<String, String>()

        const val MAX_AMOUNT_IDENTS_IN_GRAPH_QUERY = 15
    }

    fun harTilgangTilEnhetOgYtelse(ident: String, enhetId: String, ytelse: Ytelse): Boolean {
        return getEnheterMedYtelserForSaksbehandler(ident).enheter.firstOrNull { it.enhet.enhetId == enhetId }?.ytelser?.contains(
            ytelse
        ) ?: false
    }

    fun harTilgangTilYtelse(ident: String, ytelse: Ytelse): Boolean {
        return getEnheterMedYtelserForSaksbehandler(ident).enheter.flatMap { it.ytelser }.contains(ytelse)
    }

    fun getEnheterMedYtelserForSaksbehandler(ident: String): EnheterMedLovligeYtelser =
        listOf(azureGateway.getDataOmInnloggetSaksbehandler().enhet).berikMedYtelser()

    fun getEnheterForSaksbehandler(ident: String): List<Enhet> =
        listOf(azureGateway.getDataOmInnloggetSaksbehandler().enhet)

    fun getEnhetForSaksbehandler(ident: String): Enhet =
        azureGateway.getDataOmInnloggetSaksbehandler().enhet

    private fun List<Enhet>.berikMedYtelser(): EnheterMedLovligeYtelser {
        return EnheterMedLovligeYtelser(this.map {
            EnhetMedLovligeYtelser(
                enhet = it,
                ytelser = getYtelserForEnhet(it)
            )
        })
    }

    private fun getYtelserForEnhet(enhet: Enhet): List<Ytelse> =
        klageenhetTilYtelser.filter { it.key.navn == enhet.enhetId }.flatMap { it.value }

    fun getAlleSaksbehandlerIdenter(): List<String> {
        return azureGateway.getGroupMembersNavIdents(saksbehandlerRole)
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

    fun erFagansvarlig(ident: String): Boolean = getRoller(ident).hasRole(fagansvarligRole)

    fun erLeder(ident: String): Boolean = getRoller(ident).hasRole(lederRole)

    fun erSaksbehandler(ident: String): Boolean = getRoller(ident).hasRole(saksbehandlerRole)

    fun kanBehandleFortrolig(ident: String): Boolean = getRoller(ident).hasRole(kanBehandleFortroligRole)

    fun kanBehandleStrengtFortrolig(ident: String): Boolean =
        getRoller(ident).hasRole(kanBehandleStrengtFortroligRole)

    fun kanBehandleEgenAnsatt(ident: String): Boolean = getRoller(ident).hasRole(kanBehandleEgenAnsattRole)

    private fun getRoller(ident: String): List<String> = try {
        azureGateway.getRolleIder(ident)
    } catch (e: Exception) {
        logger.warn("Failed to retrieve roller for navident $ident, using emptylist instead")
        emptyList()
    }

    fun getSaksbehandlereSomKanBehandleFortrolig(): List<String> =
        azureGateway.getGroupMembersNavIdents(kanBehandleFortroligRole)


    fun getSaksbehandlereSomKanBehandleEgenAnsatt(): List<String> =
        azureGateway.getGroupMembersNavIdents(kanBehandleEgenAnsattRole)


    private fun List<String>.hasRole(role: String) = any { it.contains(role) }

    fun getNameForSaksbehandler(navIdent: String): String {
        return azureGateway.getPersonligDataOmSaksbehandlerMedIdent(navIdent).sammensattNavn
    }
}