package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.AdminService
import no.nav.klage.oppgave.service.StatistikkTilDVHResenderService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController

@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
class AdminController(
    private val adminService: AdminService,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val eregClient: EregClient,
    private val azureGateway: AzureGateway,
    private val statistikkTilDVHResenderService: StatistikkTilDVHResenderService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/internal/elasticadmin/rebuild", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun resetElasticIndexWithPost() {
        krevAdminTilgang()
        try {
            adminService.recreateEsIndex()
            adminService.syncEsWithDb()
            adminService.findAndLogOutOfSyncKlagebehandlinger()
        } catch (e: Exception) {
            logger.warn("Failed to reset ES index", e)
            throw e
        }
    }

    @PostMapping("/internal/dvh/resend", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun resendAllToDVH() {
        krevAdminTilgang()
        try {
            statistikkTilDVHResenderService.resendAllKlagebehandlinger()
        } catch (e: Exception) {
            logger.warn("Failed to resend all klagebehandlinger to DVH", e)
            throw e
        }
    }

    private fun krevAdminTilgang() {
        if (!innloggetSaksbehandlerRepository.erAdmin()) {
            throw MissingTilgangException("Not an admin")
        }
    }

}