package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.service.ForlengetBehandlingstidService
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/behandlinger/{behandlingId}/forlengetbehandlingstid")
class ForlengetBehandlingstidController(
    private val forlengetBehandlingstidService: ForlengetBehandlingstidService,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PutMapping("/title")
    fun setTitle(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidTitleInput
    ) {
        logMethodDetails(
            methodName = ::setTitle.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )

        return forlengetBehandlingstidService.setTitle(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/fullmektig-fritekst")
    fun setFullmektigFritekst(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidFullmektigFritekstInput
    ) {
        logMethodDetails(
            methodName = ::setFullmektigFritekst.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidService.setFullmektigFritekst(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/custom-text")
    fun setCustomText(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidCustomTextInput
    ) {
        logMethodDetails(
            methodName = ::setCustomText.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidService.setCustomText(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/reason")
    fun setReason(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidCustomTextInput
    ) {
        logMethodDetails(
            methodName = ::setReason.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidService.setReason(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/behandlingstid-units")
    fun setBehandlingstidUnits(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidVarsletBehandlingstidUnitsInput
    ) {
        logMethodDetails(
            methodName = ::setBehandlingstidUnits.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidService.setBehandlingstidUnits(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/behandlingstid-unit-type-id")
    fun setBehandlingstidUnitTypeId(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidVarsletBehandlingstidUnitTypeIdInput
    ) {
        logMethodDetails(
            methodName = ::setBehandlingstidUnitTypeId.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidService.setBehandlingstidUnitTypeId(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/behandlingstid-date")
    fun setBehandlingstidDate(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidBehandlingstidDateInput
    ) {
        logMethodDetails(
            methodName = ::setBehandlingstidDate.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidService.setBehandlingstidDate(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/receivers")
    fun setReceivers(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidReceiversInput
    ) {
        logMethodDetails(
            methodName = ::setReceivers.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidService.setReceivers(behandlingId = behandlingId, input = input)
    }

}