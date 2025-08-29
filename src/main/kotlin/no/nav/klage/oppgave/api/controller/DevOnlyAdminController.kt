package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import no.nav.klage.oppgave.api.view.ExternalFeilregistreringInput
import no.nav.klage.oppgave.clients.klagefssproxy.KlageFssProxyClient
import no.nav.klage.oppgave.clients.klagefssproxy.domain.FeilregistrertInKabalInput
import no.nav.klage.oppgave.clients.klagefssproxy.domain.SakFromKlanke
import no.nav.klage.oppgave.clients.pdl.PersonCacheService
import no.nav.klage.oppgave.service.AdminService
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.net.InetAddress
import java.util.*

@Profile("dev-gcp")
@RestController
@RequestMapping("/internal/dev")
class DevOnlyAdminController(
    private val adminService: AdminService,
    private val tokenUtil: TokenUtil,
    private val behandlingService: BehandlingService,
    private val klageFssProxyClient: KlageFssProxyClient,
    private val personCacheService: PersonCacheService,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Unprotected
    @GetMapping("/kafkaadmin/refill", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun resetElasticIndex() {
        try {
            logger.info("Syncing db with Kafka")
            adminService.syncKafkaWithDb()
        } catch (e: Exception) {
            logger.warn("Failed to resync db with Kafka")
            throw e
        }
    }

    @Unprotected
    @DeleteMapping("/behandlinger/{id}", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun deleteBehandling(@PathVariable("id") behandlingId: UUID) {
        try {
            logger.info("Delete behandling in dev")
            adminService.deleteBehandlingInDev(behandlingId)
        } catch (e: Exception) {
            logger.warn("Failed to delete behandling in dev", e)
            throw e
        }
    }

    @Unprotected
    @GetMapping("/behandlinger/{id}/reindexdev", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun reindexBehandling(@PathVariable("id") behandlingId: UUID) {
        try {
            logger.info("Reindexing behandling in dev")
            adminService.reindexBehandlingInSearch(behandlingId)
        } catch (e: Exception) {
            logger.warn("Failed to reindex behandling i dev", e)
            throw e
        }
    }

    @Unprotected
    @PostMapping("/generatemissingankeitrygderettendev", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun generateMissingAnkeITrygderetten() {
        logger.debug("generateMissingAnkeITrygderetten is called in dev")

        try {
            adminService.generateMissingAnkeITrygderetten()
        } catch (e: Exception) {
            logger.warn("Failed to generate missing AnkeITrygderetten", e)
            throw e
        }
    }

    @Unprotected
    @PostMapping("/migratedvh")
    fun migrateDvhEvents() {
        logger.debug("migrateDvhEvents is called")

        try {
            adminService.migrateDvhEvents()
        } catch (e: Exception) {
            logger.warn("Failed to migrate DVH events", e)
            throw e
        }
    }

    @Unprotected
    @GetMapping("/mytokens")
    fun getTokens(): Map<String, String> {
        return mapOf(
            "getAccessTokenFrontendSent" to tokenUtil.getAccessTokenFrontendSent(),
            "getSaksbehandlerAccessTokenWithGraphScope" to tokenUtil.getSaksbehandlerAccessTokenWithGraphScope(),
            "getSaksbehandlerAccessTokenWithSafScope" to tokenUtil.getSaksbehandlerAccessTokenWithSafScope(),
            "getSaksbehandlerAccessTokenWithPdlScope" to tokenUtil.getSaksbehandlerAccessTokenWithPdlScope(),
            "getAppAccessTokenWithGraphScope" to tokenUtil.getAppAccessTokenWithGraphScope(),
            "getSaksbehandlerAccessTokenWithDokarkivScope" to tokenUtil.getSaksbehandlerAccessTokenWithDokarkivScope(),
            "getSaksbehandlerAccessTokenWithKodeverkScope" to tokenUtil.getSaksbehandlerAccessTokenWithKodeverkScope(),
            "getOnBehalfOfTokenWithKrrProxyScope" to tokenUtil.getOnBehalfOfTokenWithKrrProxyScope(),
            "getAppAccessTokenWithPdlScope" to tokenUtil.getAppAccessTokenWithPdlScope(),
            "getAppAccessTokenWithKlageFSSProxyScope" to tokenUtil.getAppAccessTokenWithKlageFSSProxyScope(),
        )
    }

    @Unprotected
    @Operation(
        summary = "Feilregistrer sak",
        description = "Endepunkt for å feilregistrere en klage/anke som ikke skal behandles av klageinstans. Fungerer kun hvis sak ikke er tildelt saksbehandler. Ellers må KA kontaktes."
    )
    @PostMapping("/feilregistrering")
    fun feilregistrer(
        @Parameter(description = "Feilregistrering")
        @Valid @RequestBody feilregistrering: ExternalFeilregistreringInput,
    ) {
        val behandling = behandlingService.feilregistrer(
            type = feilregistrering.type,
            reason = feilregistrering.reason,
            navIdent = feilregistrering.navIdent,
            fagsystem = feilregistrering.fagsystem,
            kildereferanse = feilregistrering.kildereferanse,
        )

        if (behandling.shouldUpdateInfotrygd()) {
            logger.debug("Feilregistrering av behandling skal registreres i Infotrygd.")
            klageFssProxyClient.setToFeilregistrertInKabal(
                sakId = behandling.kildeReferanse,
                input = FeilregistrertInKabalInput(
                    saksbehandlerIdent = systembrukerIdent,
                )
            )
            logger.debug("Feilregistrering av behandling ble registrert i Infotrygd.")
        }
    }

    @Unprotected
    @GetMapping("/setsortkeytodua")
    fun setSortKeyToDUA() {
        logger.debug("setSortKeyToDUA is called in dev")

        try {
            adminService.setSortKeyToDUA()
        } catch (e: Exception) {
            logger.warn("Failed to setSortKeyToDUA", e)
            throw e
        }
    }

    @Unprotected
    @GetMapping("/infotrygdsaker/{id}")
    fun getInfotrygdsak(
        @PathVariable("id") id: String
    ): SakFromKlanke {
        logger.debug("getInfotrygdsak is called in dev")

        return adminService.getInfotrygdsak(id)
    }

    @Unprotected
    @GetMapping(value = ["/enableminsidemicrofrontend/{behandlingId}", "/enableminsidemicrofrontend"])
    fun enableMinsideMicrofrontends(
        @PathVariable(required = false, name = "behandlingId") behandlingId: UUID?
    ) {
        logger.debug("{} is called in dev. BehandlingId: {}", ::enableMinsideMicrofrontends.name, behandlingId)

        try {
            if (behandlingId != null) {
                adminService.enableMinsideMicrofrontend(behandlingId)
            } else {
                adminService.enableAllMinsideMicrofrontends()
            }
        } catch (e: Exception) {
            logger.warn("Failed in ${::enableMinsideMicrofrontends.name}", e)
            throw e
        }
    }

    @Unprotected
    @GetMapping(value = ["/disableminsidemicrofrontend/{behandlingId}", "/disableminsidemicrofrontend"])
    fun disableMinsideMicrofrontends(
        @PathVariable(required = false, name = "behandlingId") behandlingId: UUID?
    ) {
        logger.debug("{} is called in dev. BehandlingId: {}", ::disableMinsideMicrofrontends.name, behandlingId)

        try {
            if (behandlingId != null) {
                adminService.disableMinsideMicrofrontend(behandlingId)
            } else {
                adminService.disableAllMinsideMicrofrontends()
            }
        } catch (e: Exception) {
            logger.warn("Failed in ${::disableMinsideMicrofrontends.name}", e)
            throw e
        }
    }

    @Unprotected
    @GetMapping("/log-inaccessible")
    fun logInaccessible() {
        logger.debug("logInaccessible is called")
        adminService.logInaccessibleBehandlinger()
    }

    @Unprotected
    @GetMapping("/log-inaccessible-skjerming")
    fun logInaccessibleSkjerming() {
        logger.debug("logInaccessibleSkjerming is called")
        adminService.checkForUnavailableDueToBeskyttelseAndSkjerming(null)
    }

    @Unprotected
    @GetMapping("/log-inaccessible-hjemler")
    fun logInaccessibleHjemler() {
        logger.debug("logInaccessibleHjemler is called")
        adminService.checkForUnavailableDueToHjemler(null)
    }

    @Unprotected
    @GetMapping("/empty-person-cache")
    fun emptyPersonCache() {
        logger.debug("emptyPersonCache is called")
        adminService.emptyPersonCache()
    }


    @Unprotected
    @GetMapping("/evictallcaches", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun evictAllCAches() {
        logger.debug("${::evictAllCAches.name} is called")
        try {
            logger.info("Evicting all caches")
            adminService.evictAllCaches()
        } catch (e: Exception) {
            logger.warn("Failed to evict all caches", e)
            throw e
        }
    }

    @Unprotected
    @GetMapping(value = ["/cache/{fnr}", "/cache"], produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun getPersonFromCache(
        @PathVariable(required = false, name = "fnr") fnr: String?
    ): Pair<String?, Any> {
        logger.debug("${::getPersonFromCache.name} is called")
        try {
            logger.info("Getting person from cache.")
            return if (fnr.isNullOrBlank()) {
                InetAddress.getLocalHost().hostName to personCacheService.getCache()
            } else {
                InetAddress.getLocalHost().hostName to personCacheService.getPerson(foedselsnr = fnr)
            }
        } catch (e: Exception) {
            logger.warn("Failed to get from cache", e)
            throw e
        }
    }

    data class Fnr(val fnr: String)
}