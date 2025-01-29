package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.api.view.TaskListMerkantilView
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.service.AdminService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/internal")
class AdminController(
    private val adminService: AdminService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val azureGateway: AzureGateway,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/kafkaadmin/refill", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun refillKafka() {
        azureGateway.getDataOmInnloggetSaksbehandler()
        azureGateway.getRollerForInnloggetSaksbehandler()

        krevAdminTilgang()

        try {
            logger.info("Syncing db with Kafka")
            adminService.syncKafkaWithDb()
            logger.info("Finished syncing db with Kafka")
        } catch (e: Exception) {
            logger.warn("Failed to resync db with Kafka")
            throw e
        }
    }

    @PostMapping("/dvh/resend", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun resendStatsToDVH() {
        logger.debug("resendStatsToDVH is called")
        krevAdminTilgang()

        try {
            adminService.resendToDVH()
        } catch (e: Exception) {
            logger.warn("Failed to resend to DVH", e)
            throw e
        }
    }

    @PostMapping("/generatemissingankeitrygderetten", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun generateMissingAnkeITrygderetten() {
        logger.debug("generateMissingAnkeITrygderetten is called")
        krevAdminTilgang()

        try {
            adminService.generateMissingAnkeITrygderetten()
        } catch (e: Exception) {
            logger.warn("Failed to generate missing AnkeITrygderetten", e)
            throw e
        }
    }

    @PostMapping("/isskjermet")
    @ResponseStatus(HttpStatus.OK)
    fun isSkjermet(
        @RequestBody input: Fnr
    ) {
        logger.debug("isSkjermet is called")
        krevAdminTilgang()

        adminService.isSkjermet(input.fnr)
    }

    @PostMapping("/migratedvhevents")
    fun migrateDvhEvents() {
        logger.debug("migrateDvhEvents is called")
        krevAdminTilgang()

        try {
            adminService.migrateDvhEvents()
        } catch (e: Exception) {
            logger.warn("Failed to migrate DVH events", e)
            throw e
        }
    }

    @GetMapping("/invalidregistreringshjemler")
    fun getInvalidRegistreringshjemler() {
        logger.debug("getInvalidRegistreringshjemler is called")
        krevAdminTilgang()

        adminService.logInvalidRegistreringshjemler()
    }

    @GetMapping("/logexpiredusers")
    fun logExpiredUsers() {
        logger.debug("logExpiredUsers is called")
        krevAdminTilgang()

        adminService.logExpiredUsers()
    }

    @GetMapping("/logprotected")
    fun logProtected() {
        logger.debug("logProtected is called")
        krevAdminTilgang()

        adminService.logProtected()
    }

    @PostMapping("/setsortkeytodua")
    fun setSortKeyToDUA() {
        logger.debug("setSortKeyToDUA is called")
        krevAdminTilgang()

        try {
            adminService.setSortKeyToDUA()
        } catch (e: Exception) {
            logger.warn("Failed to setSortKeyToDUA", e)
            throw e
        }
    }

    @GetMapping("/behandlinger/{id}/reindex", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun reindexBehandling(@PathVariable("id") behandlingId: UUID) {
        logger.debug("reindexBehandling is called")
        krevAdminTilgang()
        try {
            logger.info("Reindexing behandling")
            adminService.reindexBehandlingInSearch(behandlingId)
        } catch (e: Exception) {
            logger.warn("Failed to reindex behandling", e)
            throw e
        }
    }

    @GetMapping("/migrateTilbakekreving", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun migrateTilbakekreving() {
        logger.debug("migrateTilbakekreving is called")
        krevAdminTilgang()
        try {
            logger.info("Migrating tilbakekreving")
            adminService.migrateTilbakekreving()
        } catch (e: Exception) {
            logger.warn("Failed to migrate tilbakekreving", e)
            throw e
        }
    }

    @GetMapping("/merkantil-tasks")
    fun getTaskListMerkantil(): List<TaskListMerkantilView> {
        logger.debug("getTaskListMerkantil is called")
        krevAdminTilgang()

        return adminService.getTaskListMerkantil()
    }

    @GetMapping("/enableminsidemicrofrontend/{behandlingId}")
    fun enableMinsideMicrofrontends(
        @PathVariable("behandlingId") behandlingId: UUID?
    ) {
        logger.debug("{} is called in dev. BehandlingId: {}", ::enableMinsideMicrofrontends.name, behandlingId)
        krevAdminTilgang()
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

    @GetMapping("/disableeminsidemicrofrontend/{behandlingId}")
    fun disableMinsideMicrofrontends(
        @PathVariable("behandlingId") behandlingId: UUID?
    ) {
        logger.debug("{} is called in dev. BehandlingId: {}", ::disableMinsideMicrofrontends.name, behandlingId)
        krevAdminTilgang()
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

    data class Fnr(val fnr: String)

    private fun krevAdminTilgang() {
        if (!innloggetSaksbehandlerService.isKabalAdmin()) {
            throw MissingTilgangException("Not an admin")
        }
    }

}