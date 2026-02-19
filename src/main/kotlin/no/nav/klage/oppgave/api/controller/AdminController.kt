package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.api.view.TaskListMerkantilView
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.exceptions.MissingTilgangException
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

    @GetMapping("/behandlinger/reindex/anker-i-tr", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun reindexBehandlingAnkerITr() {
        logger.debug("reindexBehandling is called")
        krevAdminTilgang()
        try {
            logger.info("Reindexing behandling in batch")
            val idList = listOf(
                UUID.fromString("f3f8d79e-08d1-487f-990a-7fb811d0dd4a"),
                UUID.fromString("0bd02526-6078-4db9-b3e7-d60ed742c086"),
                UUID.fromString("8e196397-7dcc-4994-99ea-bb1d8db670a5"),
                UUID.fromString("48dff576-a093-45b6-886f-3f68d2192e9a"),
                UUID.fromString("d9487aee-1ab5-4c88-9f2f-086ab18c3c70"),
                UUID.fromString("e172b2c9-000d-4753-b236-9fbe154f5781"),
                UUID.fromString("68f3f30b-a853-4cd9-b64d-b8638626d66b"),
                UUID.fromString("cf58a652-bb09-47c5-9fe3-0d14311703a1"),
                UUID.fromString("28a6c0f4-8407-4d49-bc64-a137865438bb"),
                UUID.fromString("71bf490b-9a92-41a5-95eb-93fed396519b"),
                UUID.fromString("84a6ddf3-87b0-4291-995b-cfc4e3a90626"),
                UUID.fromString("9a4740a8-7d4f-4a01-bd4f-f36def971325"),
                UUID.fromString("85936eb5-b46d-4a5f-b9f0-aa2f6eccabdb"),
                UUID.fromString("ab22ae85-35dc-402a-8e30-c740506e7dca"),
                UUID.fromString("4d7caa45-9baa-4764-a837-dc88da947024"),
                UUID.fromString("9c940c35-c7d6-4fd2-8d55-c07d8f26847f"),
                UUID.fromString("21a8758e-ee66-4922-aee6-78bdf2810b89"),
                UUID.fromString("bb301a40-eed1-4742-bf7f-ce806df95c9a"),
                UUID.fromString("0a5ca9be-3481-4e2b-a584-c482f9f6e8b2"),
                UUID.fromString("0952abe8-f8bf-4be9-af27-71cc20f07803"),
                UUID.fromString("2403cb29-5f8a-4277-bfc8-9234725697fc"),
                UUID.fromString("afb33caa-3c4d-40d3-9df6-c0e722a9e43a"),
                UUID.fromString("6b9f903e-6ef3-40da-86a0-26944ac9eaaf"),
                UUID.fromString("6f8aaaa7-5a76-418b-a70e-005801f2681d"),
                UUID.fromString("e8f83c4a-c6f9-4c59-b5a1-ad2c39947570"),
                UUID.fromString("debace1a-b961-4a57-b9c0-60cc61bfb594"),
                UUID.fromString("64f66813-2201-4e9f-9a4a-b0cc699e7e04"),
                UUID.fromString("a9cf6846-c726-404c-9b7d-a5418217dda3"),
                UUID.fromString("f406ebd7-03d8-4e0b-8faa-de35fc444f0d"),
                UUID.fromString("1ad719cd-1f5d-45f4-b503-6c764f130224"),
                UUID.fromString("29ec5723-76ed-410a-a873-328c26f12c64"),
                UUID.fromString("f9ec3280-64ee-42d9-957c-ca3f59fea3ee"),
                UUID.fromString("4dfae333-ac56-4976-ad9b-815b921b92e8"),
                UUID.fromString("b1954102-b479-46b0-b3ce-5142e5c0c40e"),
                UUID.fromString("4e764885-43d0-4524-b65a-d04ba40e6488"),
                UUID.fromString("034a2945-b014-46a0-aa2b-9d5310766d4f"),
                UUID.fromString("cc0e92f8-f618-4791-af26-aa14bb85281c"),
                UUID.fromString("b404fcc0-454b-4aac-8012-b173670f2fec"),
                UUID.fromString("8d4ea58e-0223-490e-b899-5bdbee6bf0cb"),
            )
            idList.forEach { adminService.reindexBehandlingInSearch(it) }
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

    @GetMapping("/set-previous-behandling-id")
    @ResponseStatus(HttpStatus.OK)
    fun setPreviousBehandlingId(
        @RequestParam(value = "dryRun", required = false, defaultValue = "true") dryRun: Boolean = true,
    ) {
        logger.debug("setPreviousBehandlingId is called")
        krevAdminTilgang()
        adminService.setPreviousBehandlingId(dryRun = dryRun)
    }

    @GetMapping("/reload-cache-all-behandlinger")
    @ResponseStatus(HttpStatus.OK)
    fun reloadCacheAllBehandlinger() {
        logger.debug("reloadCacheAllBehandlinger is called")
        adminService.resetPersonCacheFromAllBehandlinger()
    }

    @GetMapping("/reload-cache-open-behandlinger")
    @ResponseStatus(HttpStatus.OK)
    fun reloadCacheOpenBehandlinger() {
        logger.debug("reloadCacheOpenBehandlinger is called")
        adminService.resetPersonCacheFromOpenBehandlinger()
    }

    @GetMapping(value = ["/opprettet-event/{behandlingId}", "/opprettet-event"], produces = ["application/json"])
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

    data class Comment(val comment: String)

    private fun krevAdminTilgang() {
        if (!innloggetSaksbehandlerService.isKabalAdmin()) {
            throw MissingTilgangException("Not an admin")
        }
    }

}