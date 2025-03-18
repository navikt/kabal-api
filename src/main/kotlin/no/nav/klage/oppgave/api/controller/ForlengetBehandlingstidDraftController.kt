package no.nav.klage.oppgave.api.controller

import no.nav.klage.dokument.api.view.MottakerInput
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.service.ForlengetBehandlingstidDraftService
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
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

    @PostMapping
    fun getOrCreateForlengetBehandlingstidDraft(@PathVariable behandlingId: UUID): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::getOrCreateForlengetBehandlingstidDraft.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.getOrCreateForlengetBehandlingstidDraft(behandlingId = behandlingId)
    }

    @ResponseBody
    @GetMapping("/pdf")
    fun getPdf(@PathVariable behandlingId: UUID): ResponseEntity<ByteArray> {
        logMethodDetails(
            methodName = ::getPdf.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )

        forlengetBehandlingstidDraftService.getPdf(
            behandlingId = behandlingId
        ).let {
            val responseHeaders = HttpHeaders()
            responseHeaders.contentType = MediaType.APPLICATION_PDF
            responseHeaders.add("Content-Disposition", "inline; filename=forlenget-behandlingstid-preview.pdf")
            return ResponseEntity(
                it,
                responseHeaders,
                HttpStatus.OK
            )
        }
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

    @PutMapping("/previous-behandlingstid-info")
    fun setPreviousBehandlingstidInfo(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidPreviousBehandlingstidInfoInput
    ): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::setPreviousBehandlingstidInfo.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.setPreviousBehandlingstidInfo(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/reason-no-letter")
    fun setReasonNoLetter(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidReasonNoLetterInput
    ): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::setReasonNoLetter.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.setReasonNoLetter(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/do-not-send-letter")
    fun setDoNotSendLetter(
        @PathVariable behandlingId: UUID,
        @RequestBody input: ForlengetBehandlingstidDoNotSendLetterInput
    ): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::setDoNotSendLetter.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.setDoNotSendLetter(behandlingId = behandlingId, input = input)
    }

    @PutMapping("/receivers")
    fun setReceivers(
        @PathVariable behandlingId: UUID,
        @RequestBody input: MottakerInput,
    ): ForlengetBehandlingstidDraftView {
        logMethodDetails(
            methodName = ::setReceivers.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        return forlengetBehandlingstidDraftService.setReceivers(behandlingId = behandlingId, input = input)
    }

    @PostMapping("/complete")
    fun completeDraft(
        @PathVariable behandlingId: UUID
    ) {
        logMethodDetails(
            methodName = ::completeDraft.name,
            innloggetIdent = tokenUtil.getIdent(),
            logger = logger,
        )
        forlengetBehandlingstidDraftService.completeDraft(behandlingId = behandlingId)
    }

}