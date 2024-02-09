package no.nav.klage.oppgave.api.controller


import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.api.view.IdentifikatorInput
import no.nav.klage.oppgave.api.view.PostnummerInput
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.KodeverkService
import no.nav.klage.oppgave.service.PartSearchService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*


@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
class SearchController(
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val partSearchService: PartSearchService,
    private val kodeverkService: KodeverkService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/searchpart")
    fun searchPart(
        @RequestBody input: IdentifikatorInput,
    ): BehandlingDetaljerView.PartView {
        logMethodDetails(
            ::searchPart.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return partSearchService.searchPart(input.identifikator)
    }

    @PostMapping("/searchperson")
    fun searchPerson(
        @RequestBody input: IdentifikatorInput,
    ): BehandlingDetaljerView.SakenGjelderView {
        logMethodDetails(
            ::searchPerson.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return partSearchService.searchPerson(input.identifikator)
    }

    @GetMapping("/searchpoststed/{postnummer}")
    fun searchPoststed(
        @PathVariable postnummer: String,
    ): String {
        logMethodDetails(
            ::searchPoststed.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return kodeverkService.getPoststed(postnummer)
    }

    @GetMapping("/landkoder/")
    fun getLandkoder(): List<KodeverkService.Landkode> {
        logMethodDetails(
            ::getLandkoder.name,
            innloggetSaksbehandlerService.getInnloggetIdent(),
            logger
        )

        return kodeverkService.getLandkoder()
    }
}