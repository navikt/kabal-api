package no.nav.klage.kaptein.api.controller

import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import no.nav.klage.kaptein.api.view.AnonymousBehandlingView
import no.nav.klage.kaptein.service.KapteinService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "kabal-api-kaptein")
@RequestMapping("/api/kaptein")
class KapteinController(
    private val kapteinService: KapteinService,
) {
    @ApiResponse(
        responseCode = "200",
        description = "NDJSON stream",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_NDJSON_VALUE,
                schema = Schema(implementation = AnonymousBehandlingView::class),
            ),
        ],
    )
    @GetMapping("/behandlinger-stream")
    fun getBehandlingerStream(httpServletResponse: HttpServletResponse) {
        kapteinService.writeBehandlingerStreamedToOutputStreamAsNDJson(httpServletResponse)
    }
}
