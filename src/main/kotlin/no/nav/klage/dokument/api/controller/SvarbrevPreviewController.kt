package no.nav.klage.dokument.api.controller


import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.dokument.api.view.PreviewSvarbrevAnonymousInput
import no.nav.klage.dokument.api.view.PreviewSvarbrevInput
import no.nav.klage.dokument.service.KabalJsonToPdfService
import no.nav.klage.dokument.service.SvarbrevPreviewService
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.service.PartSearchService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "kabal-api-dokumenter")
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
class SvarbrevPreviewController(
    private val svarbrevPreviewService: SvarbrevPreviewService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @ResponseBody
    @PostMapping("/svarbrev-preview")
    fun getSvarbrevPreview(
        @RequestBody input: PreviewSvarbrevInput,
    ): ResponseEntity<ByteArray> {
        logger.debug("Kall mottatt på getSvarbrevPreview")

        svarbrevPreviewService.getSvarbrevPreviewPDF(
            input = input
        ).let {
            val responseHeaders = HttpHeaders()
            responseHeaders.contentType = MediaType.APPLICATION_PDF
            responseHeaders.add("Content-Disposition", "inline; filename=svarbrev-preview.pdf")
            return ResponseEntity(
                it,
                responseHeaders,
                HttpStatus.OK
            )
        }
    }

    @ResponseBody
    @PostMapping("/svarbrev-preview/anonymous")
    fun getSvarbrevPreviewAnonymous(
        @RequestBody input: PreviewSvarbrevAnonymousInput,
    ): ResponseEntity<ByteArray> {
        logger.debug("Kall mottatt på getSvarbrevPreviewAnonymous")

        svarbrevPreviewService.getAnonymousSvarbrevPreviewPDF(input = input).let {
            val responseHeaders = HttpHeaders()
            responseHeaders.contentType = MediaType.APPLICATION_PDF
            responseHeaders.add("Content-Disposition", "inline; filename=svarbrev-preview.pdf")
            return ResponseEntity(
                it,
                responseHeaders,
                HttpStatus.OK
            )
        }
    }
}