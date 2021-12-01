package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import no.nav.klage.oppgave.api.mapper.KlagebehandlingMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logKlagebehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/klagebehandlinger")
class KlagebehandlingController(
    private val klagebehandlingService: KlagebehandlingService,
    private val klagebehandlingMapper: KlagebehandlingMapper,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PutMapping("/{id}/medunderskriverident")
    fun putMedunderskriverident(
        @PathVariable("id") klagebehandlingId: UUID,
        @RequestBody input: KlagebehandlingMedunderskriveridentInput
    ): MedunderskriverFlytResponse {
        logKlagebehandlingMethodDetails(
            "putMedunderskriverident",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )
        val klagebehandling = klagebehandlingService.setMedunderskriverIdentAndMedunderskriverFlyt(
            klagebehandlingId,
            input.medunderskriverident,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return klagebehandlingMapper.mapToMedunderskriverFlytResponse(klagebehandling)
    }


    @ApiOperation(
        value = "Flytter klagebehandlingen mellom saksbehandler og medunderskriver.",
        notes = "Flytter fra saksbehandler til medunderskriver dersom saksbehandler utfører, flytter til saksbehandler med returnert-status dersom medunderskriver utfører."
    )
    @PostMapping("/{id}/send")
    fun switchMedunderskriverFlyt(
        @ApiParam(value = "Id til klagebehandlingen i vårt system")
        @PathVariable("id") klagebehandlingId: UUID
    ): MedunderskriverFlytResponse {
        logger.debug("switchMedunderskriverFlyt is requested for klagebehandling: {}", klagebehandlingId)

        val klagebehandling = klagebehandlingService.switchMedunderskriverFlyt(
            klagebehandlingId,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return klagebehandlingMapper.mapToMedunderskriverFlytResponse(klagebehandling)
    }

    @PostMapping("/{id}/fullfoer")
    fun fullfoerKlagebehandling(
        @PathVariable("id") klagebehandlingId: UUID
    ): KlagebehandlingFullfoertView {
        logKlagebehandlingMethodDetails(
            "fullfoerKlagebehandling",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )
        val klagebehandling = klagebehandlingService.ferdigstillKlagebehandling(
            klagebehandlingId,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return klagebehandlingMapper.mapToKlagebehandlingFullfoertView(klagebehandling)
    }

    /**
     * Valgfri validering før innsending/fullføring.
     * Gjøres uansett ved fullføring av behandlingen.
     */
    @GetMapping("/{id}/validate")
    fun validate(
        @PathVariable("id") klagebehandlingId: UUID
    ): ValidationPassedResponse {
        logKlagebehandlingMethodDetails(
            "validate",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )
        klagebehandlingService.validateKlagebehandlingBeforeFinalize(
            klagebehandlingService.getKlagebehandling(
                klagebehandlingId
            )
        )
        return ValidationPassedResponse()
    }

    //TODO: Fjern når FE slutter å bruke.
    @GetMapping("/{id}/medunderskriverinfo")
    fun getMedunderskriverInfo(
        @PathVariable("id") klagebehandlingId: UUID
    ): MedunderskriverInfoView {
        logKlagebehandlingMethodDetails(
            "getMedunderskriverInfo",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )
        val klagebehandling = klagebehandlingService.getKlagebehandling(klagebehandlingId)
        return klagebehandlingMapper.mapToMedunderskriverInfoView(klagebehandling)
    }

    @GetMapping("/{id}/medunderskriver")
    fun getMedunderskriver(
        @PathVariable("id") klagebehandlingId: UUID
    ): MedunderskriverView {
        logKlagebehandlingMethodDetails(
            "getMedunderskriver",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )
        val klagebehandling = klagebehandlingService.getKlagebehandling(klagebehandlingId)
        return klagebehandlingMapper.mapToMedunderskriverView(klagebehandling)
    }

    @GetMapping("/{id}/medunderskriverflyt")
    fun getMedunderskriverFlyt(
        @PathVariable("id") klagebehandlingId: UUID
    ): MedunderskriverFlytView {
        logKlagebehandlingMethodDetails(
            "getMedunderskriverFlyt",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )
        val klagebehandling = klagebehandlingService.getKlagebehandling(klagebehandlingId)
        return klagebehandlingMapper.mapToMedunderskriverFlytView(klagebehandling)
    }
}