package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import no.nav.klage.oppgave.api.view.OversendtAnkeITrygderettenFromArena
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.AnkeITrygderettenbehandlingService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
class AnkeITrygderettenFromArenaController(
    private val ankeITrygderettenbehandlingService: AnkeITrygderettenbehandlingService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Operation(
        summary = "Send inn anker i trygderetten til Kabal, spesifikt for tilfeller i Arena.",
        description =
            "Endepunkt for å registrere anker som allerede har blitt oversendt til Trygderetten, spesifikt for tilfeller i Arena.",
    )
    @PostMapping("/ankeritrygderetten-from-arena")
    fun sendInnAnkeITrygderettenFromArena(
        @Valid @RequestBody oversendtAnkeITrygderettenFromArena: OversendtAnkeITrygderettenFromArena,
    ): UUID =
        ankeITrygderettenbehandlingService.createAnkeITrygderettenbehandlingFromArena(
            oversendtAnkeITrygderettenFromArena,
        )
}
