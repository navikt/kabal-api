package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.service.AdminService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
class AdminController(
    private val adminService: AdminService,
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val azureGateway: AzureGateway,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/internal/kafkaadmin/refill", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun refillKafka() {
        azureGateway.getDataOmInnloggetSaksbehandler()
        azureGateway.getRollerForInnloggetSaksbehandler()

        krevAdminTilgang()

        try {
            logger.info("Syncing db with Kafka")
            adminService.syncKafkaWithDb()
        } catch (e: Exception) {
            logger.warn("Failed to resync db with Kafka")
            throw e
        }
    }

    @PostMapping("/internal/dvh/resend", produces = ["application/json"])
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

    @PostMapping("/internal/generatemissingankeitrygderetten", produces = ["application/json"])
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

    @PostMapping("/internal/isskjermet")
    @ResponseStatus(HttpStatus.OK)
    fun isSkjermet(
        @RequestBody input: Fnr
    ) {
        logger.debug("isSkjermet is called")
        krevAdminTilgang()

        adminService.isSkjermet(input.fnr)
    }

    @PostMapping("/internal/migratedvhevents")
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

    @GetMapping("/internal/invalidregistreringshjemler")
    fun getInvalidRegistreringshjemler() {
        logger.debug("getInvalidRegistreringshjemler is called")
        krevAdminTilgang()

        adminService.logInvalidRegistreringshjemler()
    }

    data class Fnr(val fnr: String)

    private fun krevAdminTilgang() {
        if (!innloggetSaksbehandlerService.isKabalAdmin()) {
            throw MissingTilgangException("Not an admin")
        }
    }

}