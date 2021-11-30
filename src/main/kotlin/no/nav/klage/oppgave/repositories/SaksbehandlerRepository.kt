package no.nav.klage.oppgave.repositories

import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.ytelserPerEnhet
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
    @Value("\${ROLE_GOSYS_OPPGAVE_BEHANDLER}") private val gosysSaksbehandlerRole: String,
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

    fun harTilgangTilEnhet(ident: String, enhetId: String): Boolean {
        return getEnheterMedYtelserForSaksbehandler(ident).enheter.firstOrNull { it.enhet.enhetId == enhetId } != null
    }

    fun harTilgangTilYtelse(ident: String, ytelse: Ytelse): Boolean {
        return getEnheterMedYtelserForSaksbehandler(ident).enheter.flatMap { it.ytelser }.contains(ytelse)
    }

    fun getEnheterMedYtelserForSaksbehandler(ident: String): EnheterMedLovligeYtelser =
        axsysGateway.getEnheterForSaksbehandler(ident).berikMedYtelser()

    fun getEnheterForSaksbehandler(ident: String): List<Enhet> =
        axsysGateway.getEnheterForSaksbehandler(ident)

    private fun List<Enhet>.berikMedYtelser(): EnheterMedLovligeYtelser {
        return EnheterMedLovligeYtelser(this.map {
            EnhetMedLovligeYtelser(
                enhet = it,
                ytelser = getYtelserForEnhet(it)
            )
        })
    }

    private fun getYtelserForEnhet(enhet: Enhet): List<Ytelse> =
        if (ytelserPerEnhet.containsKey(enhet.enhetId)) {
            ytelserPerEnhet[enhet.enhetId]!!
        } else {
            logger.error("Fant ikke noen ytelse for enhet $enhet. Dette må legges til i kodebasen sporenstraks!")
            emptyList()
        }

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

    fun erSaksbehandler(ident: String): Boolean =
        getRoller(ident).hasRole(saksbehandlerRole)
                || getRoller(ident).hasRole(gosysSaksbehandlerRole)

    fun kanBehandleFortrolig(ident: String): Boolean = getRoller(ident).hasRole(kanBehandleFortroligRole)

    fun kanBehandleStrengtFortrolig(ident: String): Boolean =
        getRoller(ident).hasRole(kanBehandleStrengtFortroligRole)

    fun kanBehandleEgenAnsatt(ident: String): Boolean = getRoller(ident).hasRole(kanBehandleEgenAnsattRole)

    private fun getRoller(ident: String): List<String> = azureGateway.getRolleIder(ident)

    private fun List<String>.hasRole(role: String) = any { it.contains(role) }

    fun getNameForSaksbehandler(navIdent: String): String {
        return azureGateway.getPersonligDataOmSaksbehandlerMedIdent(navIdent).sammensattNavn
    }
}