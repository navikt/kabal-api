package no.nav.klage.dokument.api.controller


import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.dokument.api.view.PreviewAnkeSvarbrevInput
import no.nav.klage.dokument.service.KabalJsonToPdfService
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.service.PartSearchService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
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
    private val kabalJsonToPdfService: KabalJsonToPdfService,
    private val partSearchService: PartSearchService,
    private val azureGateway: AzureGateway,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @ResponseBody
    @PostMapping("/svarbrev-preview")
    fun getSvarbrevPreview(
        @RequestBody input: PreviewAnkeSvarbrevInput,
    ): ResponseEntity<ByteArray> {
        logger.debug("Kall mottatt på getSvarbrevPreview")

        val sakenGjelderName = partSearchService.searchPart(
            identifikator = input.sakenGjelder.value,
            skipAccessControl = true
        ).name

        kabalJsonToPdfService.getSvarbrevPDF(
            svarbrevInput = input.svarbrevInput,
            mottattKlageinstans = input.mottattKlageinstans,
            fristInWeeks = input.svarbrevInput.varsletBehandlingstidWeeks,
            sakenGjelderIdentifikator = input.sakenGjelder.value,
            sakenGjelderName = sakenGjelderName,
            ytelse = Ytelse.of(input.ytelseId),
            klagerIdentifikator = input.klager?.value ?: input.sakenGjelder.value,
            klagerName = if (input.klager != null) {
                partSearchService.searchPart(
                    identifikator = input.klager.value,
                    skipAccessControl = true
                ).name
            } else {
                sakenGjelderName
            },
            avsenderEnhetId = azureGateway.getDataOmInnloggetSaksbehandler().enhet.enhetId,
        ).let {
            val responseHeaders = HttpHeaders()
            responseHeaders.contentType = MediaType.APPLICATION_PDF
            responseHeaders.add("Content-Disposition", "inline; filename=svarbrev.pdf")
            return ResponseEntity(
                it,
                responseHeaders,
                HttpStatus.OK
            )
        }
    }
}