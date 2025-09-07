package no.nav.klage.kaptein.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import no.nav.klage.kaptein.service.KapteinService
import no.nav.klage.oppgave.config.SecurityConfiguration
import no.nav.security.token.support.core.api.ProtectedWithClaims
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

    @GetMapping("/behandlinger")
    fun getBehandlinger(
        httpServletResponse: HttpServletResponse,
    ) {
        kapteinService.writeBehandlingerStreamedToOutputStream(httpServletResponse)
    }

}