package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.api.view.TaskListMerkantilView
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.service.AdminService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.TaskListMerkantilService
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
    private val taskListMerkantilService: TaskListMerkantilService,
    private val behandlingRepository: BehandlingRepository,
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

    @GetMapping("/log-inaccessible")
    fun logInaccessible() {
        logger.debug("logInaccessible is called")
        krevAdminTilgang()

        adminService.logInaccessibleBehandlinger()
    }

    @GetMapping("/log-inaccessible-skjerming")
    fun logInaccessibleSkjerming() {
        logger.debug("logInaccessibleSkjerming is called")
        krevAdminTilgang()
        adminService.checkForUnavailableDueToBeskyttelseAndSkjerming(null)
    }

    @GetMapping("/log-inaccessible-hjemler")
    fun logInaccessibleHjemler() {
        logger.debug("logInaccessibleHjemler is called")
        krevAdminTilgang()
        adminService.checkForUnavailableDueToHjemler(null)
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

        return taskListMerkantilService.getTaskListMerkantil()
    }

    @PostMapping("/merkantil-tasks/{taskId}/complete", produces = ["application/json"])
    fun completeMerkantilTask(
        @PathVariable("taskId") taskId: UUID,
        @RequestBody input: Comment
    ): TaskListMerkantilView {
        logger.debug("completeMerkantilTask is called")
        krevAdminTilgang()

        try {
            return taskListMerkantilService.setCommentAndMarkTaskAsCompleted(
                taskId = taskId,
                inputComment = input.comment
            )
        } catch (e: Exception) {
            logger.warn("Failed to complete merkantil task", e)
            throw e
        }
    }

    @GetMapping(value = ["/enableminsidemicrofrontend/{behandlingId}", "/enableminsidemicrofrontend"])
    fun enableMinsideMicrofrontends(
        @PathVariable("behandlingId") behandlingId: UUID?
    ) {
        logger.debug("{} is called. BehandlingId: {}", ::enableMinsideMicrofrontends.name, behandlingId)
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

    @GetMapping(value = ["/disableminsidemicrofrontend/{behandlingId}", "/disableminsidemicrofrontend"])
    fun disableMinsideMicrofrontends(
        @PathVariable("behandlingId") behandlingId: UUID?
    ) {
        logger.debug("{} is called. BehandlingId: {}", ::disableMinsideMicrofrontends.name, behandlingId)
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

    @GetMapping("/internal/evictallcaches", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun evictAllCAches() {
        logger.debug("${::evictAllCAches.name} is called")
        krevAdminTilgang()
        try {
            logger.info("Evicting all caches")
            adminService.evictAllCaches()
        } catch (e: Exception) {
            logger.warn("Failed to evict all caches", e)
            throw e
        }
    }

    @GetMapping("/missing-in-kaka", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun fixMissingInKaka() {
        logger.debug("fixMissingInKaka is called")
        krevAdminTilgang()
        try {
            logger.info("Finishing missing in kaka")
            adminService.fixMissingInKaka()
        } catch (e: Exception) {
            logger.warn("Failed to finishing missing in kaka", e)
            throw e
        }
    }

    @GetMapping("/set-id-on-parter")
    @ResponseStatus(HttpStatus.OK)
    fun setIdOnParter() {
        logger.debug("setIdOnParter is called")
        krevAdminTilgang()
        try {
            var counter = 0
            val allBehandlinger = behandlingRepository.findAll()
            val behandlingerTotalSize = allBehandlinger.size
            val chunkSize = 100
            allBehandlinger
                .map { it.id }
                .chunked(chunkSize)
                .forEach { behandlingIdList ->
                    val start = System.currentTimeMillis()
                    adminService.setIdOnParterWithBehandlinger(behandlingIdList)
                    counter += behandlingIdList.size
                    logger.debug(
                        "setIdOnParter took {} ms for {} behandlinger. Now at {} of total {}",
                        System.currentTimeMillis() - start,
                        behandlingIdList.size,
                        counter,
                        behandlingerTotalSize,
                    )
                }
        } catch (e: Exception) {
            logger.error("Failed to set id on parter", e)
            throw e
        }
    }

    @GetMapping(value =  ["/opprettet-event/{behandlingId}", "/opprettet-event"], produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun generateOpprettetEvent(
        @PathVariable(required = false, name = "behandlingId") behandlingId: UUID? = null
    ) {
        logger.debug("generateOpprettetEvent is called, optional behandlingId: {}", behandlingId)
        krevAdminTilgang()
        try {
            logger.info("Generating opprettet events.")
            adminService.generateOpprettetEvents(behandlingId = behandlingId)
        } catch (e: Exception) {
            logger.warn("Failed to generate opprettet events", e)
            throw e
        }
    }

    data class Fnr(val fnr: String)
    data class Comment(val comment: String)

    private fun krevAdminTilgang() {
        if (!innloggetSaksbehandlerService.isKabalAdmin()) {
            throw MissingTilgangException("Not an admin")
        }
    }

}