package no.nav.klage.oppgave.api.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.api.view.EnhetView
import no.nav.klage.oppgave.api.view.GosysOppgaveMappeView
import no.nav.klage.oppgave.api.view.IdentifikatorInput
import no.nav.klage.oppgave.api.view.SearchPartWithUtsendingskanalInput
import no.nav.klage.oppgave.api.view.SearchSaksnummerResponse
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.service.EnhetService
import no.nav.klage.oppgave.service.GosysOppgaveService
import no.nav.klage.oppgave.service.InnloggetSaksbehandlerService
import no.nav.klage.oppgave.service.OppgaveService
import no.nav.klage.oppgave.service.PartSearchService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "kabal-api")
@ProtectedWithClaims(issuer = ISSUER_AAD)
class SearchController(
    private val innloggetSaksbehandlerService: InnloggetSaksbehandlerService,
    private val partSearchService: PartSearchService,
    private val gosysOppgaveService: GosysOppgaveService,
    private val oppgaveService: OppgaveService,
    private val enhetService: EnhetService,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PostMapping("/searchpart")
    fun searchPart(
        @RequestBody input: IdentifikatorInput,
    ): BehandlingDetaljerView.SearchPartView {
        logMethodDetails(
            methodName = ::searchPart.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return partSearchService.searchPart(input.identifikator)
    }

    @PostMapping("/searchpartwithutsendingskanal")
    fun searchPartWithUtsendingskanal(
        @RequestBody input: SearchPartWithUtsendingskanalInput,
    ): BehandlingDetaljerView.SearchPartViewWithUtsendingskanal {
        logMethodDetails(
            methodName = ::searchPartWithUtsendingskanal.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return partSearchService.searchPartWithUtsendingskanal(
            identifikator = input.identifikator,
            systemUserContext = false,
            sakenGjelderId = input.sakenGjelderId,
            tema = Ytelse.of(input.ytelseId).toTema(),
            systemContext = false,
        )
    }

    @PostMapping("/searchperson")
    fun searchPerson(
        @RequestBody input: IdentifikatorInput,
    ): BehandlingDetaljerView.SearchPersonView {
        logMethodDetails(
            methodName = ::searchPerson.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return partSearchService.searchPerson(input.identifikator)
    }

    @GetMapping("/search/gosysoppgavemapper/{enhetsnr}")
    fun searchGosysOppgaveMapper(
        @PathVariable("enhetsnr") enhetsnr: String,
    ): List<GosysOppgaveMappeView> {
        logMethodDetails(
            methodName = ::searchGosysOppgaveMapper.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return gosysOppgaveService.getMapperForEnhet(enhetsnr = enhetsnr)
    }

    @GetMapping("/search/enheter")
    fun searchEnheter(
        @RequestParam("enhetsnr", required = false) enhetsnr: String?,
        @RequestParam("enhetsnavn", required = false) enhetsnavn: String?,
    ): List<EnhetView> {
        logMethodDetails(
            methodName = ::searchEnheter.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return enhetService.findEnheter(enhetsnr = enhetsnr, enhetsnavn = enhetsnavn)
    }

    @GetMapping("/search/saksnummer")
    fun searchSaksnummer(
        @RequestParam("saksnummer", required = true) fagsakId: String,
    ): SearchSaksnummerResponse {
        logMethodDetails(
            methodName = ::searchSaksnummer.name,
            innloggetIdent = innloggetSaksbehandlerService.getInnloggetIdent(),
            logger = logger,
        )

        return oppgaveService.searchOppgaverByFagsakId(fagsakId = fagsakId)
    }
}
