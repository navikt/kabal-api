package no.nav.klage.oppgave.api.controller

import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.service.ForlengetBehandlingstidDraftService
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/behandlinger/{behandlingId}/forlenget-behandlingstid-draft")
class ForlengetBehandlingstidDraftController(
    private val forlengetBehandlingstidDraftService: ForlengetBehandlingstidDraftService,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping
    fun getForlengetBehandlingstidDraft(@PathVariable behandlingId: UUID): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::getForlengetBehandlingstidDraft.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.getForlengetBehandlingstidDraft(behandlingId = behandlingId)
    }

    @PutMapping("/title")
    fun setTitle(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidTitleInput
    ): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::setTitle.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )

        return forlengetBehandlingstidDraftService.setTitle(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/fullmektig-fritekst")
    fun setFullmektigFritekst(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidFullmektigFritekstInput
    ): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::setFullmektigFritekst.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.setFullmektigFritekst(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/custom-text")
    fun setCustomText(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidCustomTextInput
    ): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::setCustomText.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.setCustomText(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/reason")
    fun setReason(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidReasonInput
    ): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::setReason.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.setReason(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/behandlingstid-units")
    fun setBehandlingstidUnits(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidVarsletBehandlingstidUnitsInput
    ): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::setBehandlingstidUnits.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.setBehandlingstidUnits(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/behandlingstid-unit-type-id")
    fun setBehandlingstidUnitTypeId(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidVarsletBehandlingstidUnitTypeIdInput
    ): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::setBehandlingstidUnitTypeId.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.setBehandlingstidUnitTypeId(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/behandlingstid-date")
    fun setBehandlingstidDate(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidBehandlingstidDateInput
    ): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::setBehandlingstidDate.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.setBehandlingstidDate(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/receivers")
    fun setReceivers(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidReceiversInput
    ): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::setReceivers.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.setReceivers(behandlingId = behandlingId, input = input)
    }

}