package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import no.nav.klage.oppgave.api.view.ExternalFeilregistreringInput
import no.nav.klage.oppgave.service.AdminService
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@Profile("dev-gcp")
@RestController
class DevOnlyAdminController(
    private val adminService: AdminService,
    private val tokenUtil: TokenUtil,
    private val behandlingService: BehandlingService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Unprotected
    @GetMapping("/internal/kafkaadmin/refill", produces = ["application/json"])
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
    @DeleteMapping("/internal/behandlinger/{id}", produces = ["application/json"])
    @ResponseStatus(HttpStatus.OK)
    fun deleteBehandling(@PathVariable("id") behandlingId: UUID) {
        try {
            logger.info("Delete behandling i dev")
            adminService.deleteBehandlingInDev(behandlingId)
        } catch (e: Exception) {
            logger.warn("Failed to delete behandling i dev", e)
            throw e
        }
    }

    @Unprotected
    @PostMapping("/internal/generatemissingankeitrygderettendev", produces = ["application/json"])
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
    @PostMapping("/internal/isskjermetdev")
    @ResponseStatus(HttpStatus.OK)
    fun isSkjermet(
        @RequestBody input: Fnr
    ) {
        logger.debug("isSkjermet in dev is called")

        adminService.isSkjermet(input.fnr)
    }

    @Unprotected
    @PostMapping("/internal/migratedvh")
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
    @GetMapping("/internal/mytokens")
    fun getTokens(): Map<String, String> {
        return mapOf(
            "\ngetAccessTokenFrontendSent\n" to tokenUtil.getAccessTokenFrontendSent(),
            "\ngetSaksbehandlerAccessTokenWithGraphScope\n" to tokenUtil.getSaksbehandlerAccessTokenWithGraphScope(),
            "\ngetSaksbehandlerAccessTokenWithSafScope\n" to tokenUtil.getSaksbehandlerAccessTokenWithSafScope(),
            "\ngetSaksbehandlerAccessTokenWithPdlScope\n" to tokenUtil.getSaksbehandlerAccessTokenWithPdlScope(),
            "\ngetAppAccessTokenWithGraphScope\n" to tokenUtil.getAppAccessTokenWithGraphScope(),
        )
    }

    @Unprotected
    @Operation(
        summary = "Feilregistrer sak",
        description = "Endepunkt for å feilregistrere en klage/anke som ikke skal behandles av klageinstans. Fungerer kun hvis sak ikke er tildelt saksbehandler. Ellers må KA kontaktes."
    )
    @PostMapping("/internal/feilregistrering")
    fun feilregistrer(
        @Parameter(description = "Feilregistrering")
        @Valid @RequestBody feilregistrering: ExternalFeilregistreringInput,
    ) {
        behandlingService.feilregistrer(
            type = feilregistrering.type,
            reason = feilregistrering.reason,
            navIdent = feilregistrering.navIdent,
            fagsystem = feilregistrering.fagsystem,
            kildereferanse = feilregistrering.kildereferanse,
        )
    }

    data class Fnr(val fnr: String)
}