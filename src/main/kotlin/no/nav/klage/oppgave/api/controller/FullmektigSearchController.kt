package no.nav.klage.oppgave.api.controller


import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.FullmektigSearchService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
class FullmektigSearchController(
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val fullmektigSearchService: FullmektigSearchService
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/searchfullmektig")
    fun searchFullmektig(
        @RequestBody input: SearchFullmektigInput,
    ): BehandlingDetaljerView.ProsessfullmektigView {
        logMethodDetails(
            ::searchFullmektig.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return fullmektigSearchService.searchFullmektig(input.identifikator)
    }
}