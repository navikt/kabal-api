package no.nav.klage.kaptein.api.controller

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import no.nav.klage.kaptein.api.view.AnonymousBehandlingListView
import no.nav.klage.kaptein.api.view.AnonymousBehandlingView
import no.nav.klage.kaptein.service.KapteinService
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "kabal-api-kaptein")
@ProtectedWithClaims(issuer = SecurityConfiguration.ISSUER_AAD)
@RequestMapping("/api/kaptein")
class KapteinController(
    private val kapteinService: KapteinService,
) {

    @ApiResponse(
        responseCode = "200",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = AnonymousBehandlingListView::class)
            )
        ]
    )
    @GetMapping("/behandlinger")
    fun getBehandlinger(
        httpServletResponse: HttpServletResponse,
    ) {
        kapteinService.writeBehandlingerStreamedToOutputStream(httpServletResponse)
    }

    @ApiResponse(
        responseCode = "200", description = "NDJSON stream",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_NDJSON_VALUE,
                schema = Schema(implementation = AnonymousBehandlingView::class)
            )
        ]
    )
    @GetMapping("/behandlinger-stream")
    fun getBehandlingerStream(
        httpServletResponse: HttpServletResponse,
    ) {
        kapteinService.writeBehandlingerStreamedToOutputStreamAsNDJson(httpServletResponse)
    }

}