package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import no.nav.klage.oppgave.api.mapper.KlagebehandlingListMapper
import no.nav.klage.oppgave.api.view.KlagebehandlingEditedView
import no.nav.klage.oppgave.api.view.KlagebehandlingerListRespons
import no.nav.klage.oppgave.api.view.TilknyttetDokument
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.clients.pdl.Sivilstand
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.domain.KlagebehandlingerSearchCriteria
import no.nav.klage.oppgave.domain.kodeverk.PartIdType
import no.nav.klage.oppgave.domain.kodeverk.TemaTilgjengeligeForEktefelle.temaerTilgjengeligForEktefelle
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.ElasticsearchService
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.logKlagebehandlingMethodDetails
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.*

@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
@RequestMapping("/klagebehandlinger")
class KlagebehandlingController(
    private val klagebehandlingService: KlagebehandlingService,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val saksbehandlerService: SaksbehandlerService,
    private val environment: Environment,
    private val pdlFacade: PdlFacade,
    private val elasticsearchService: ElasticsearchService,
    private val klagebehandlingListMapper: KlagebehandlingListMapper
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @GetMapping("/{id}/relaterte")
    fun getRelaterteKlagebehandlinger(
        @PathVariable("id") klagebehandlingId: UUID
    ): KlagebehandlingerListRespons {
        logKlagebehandlingMethodDetails(
            "getRelaterteKlagebehandlinger",
            innloggetSaksbehandlerRepository.getInnloggetIdent(),
            klagebehandlingId,
            logger
        )
        //TODO: Flytt masse av dette inn i en egen service/facade, kanskje den Richard lager?
        val klagebehandling = klagebehandlingService.getKlagebehandling(klagebehandlingId)
        val lovligeTemaer =
            saksbehandlerService.findValgtEnhet(innloggetSaksbehandlerRepository.getInnloggetIdent()).temaer
        if (klagebehandling.sakenGjelder.partId.type == PartIdType.VIRKSOMHET) {
            return KlagebehandlingerListRespons(0, listOf()) //TODO: Må legge til søk mot ES på virksomhetsnummer
        }

        val sivilstand: Sivilstand? = pdlFacade.getPersonInfo(klagebehandling.sakenGjelder.partId.value).sivilstand

        val searchCriteria = KlagebehandlingerSearchCriteria(
            statuskategori = KlagebehandlingerSearchCriteria.Statuskategori.ALLE,
            ferdigstiltFom = LocalDate.now().minusMonths(12),
            foedselsnr = listOf(klagebehandling.sakenGjelder.partId.value),
            extraPersonAndTema = sivilstand?.let {
                KlagebehandlingerSearchCriteria.ExtraPersonAndTema(
                    foedselsnr = it.foedselsnr,
                    temaer = temaerTilgjengeligForEktefelle(environment).toList()
                )
            },
            offset = 0,
            limit = 100,
            projection = KlagebehandlingerSearchCriteria.Projection.UTVIDET,
        )

        val esResponse = elasticsearchService.findByCriteria(searchCriteria)
        return KlagebehandlingerListRespons(
            antallTreffTotalt = esResponse.totalHits.toInt(),
            klagebehandlinger = klagebehandlingListMapper.mapEsKlagebehandlingerToListView(
                esKlagebehandlinger = esResponse.searchHits.map { it.content },
                viseUtvidet = true,
                viseFullfoerte = true,
                saksbehandler = searchCriteria.saksbehandler,
                tilgangTilTemaer = lovligeTemaer,
                sivilstand = sivilstand
            )
        )
    }

    @PostMapping("/{id}/dokumenttilknytninger")
    fun setTilknyttetDokument(
        @PathVariable("id") klagebehandlingId: UUID,
        @RequestBody input: TilknyttetDokument
    ): KlagebehandlingEditedView {
        logKlagebehandlingMethodDetails(
            "setTilknyttetDokument", innloggetSaksbehandlerRepository.getInnloggetIdent(), klagebehandlingId,
            logger
        )
        val modified = klagebehandlingService.connectDokumentToKlagebehandling(
            klagebehandlingId = klagebehandlingId,
            journalpostId = input.journalpostId,
            dokumentInfoId = input.dokumentInfoId,
            saksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return KlagebehandlingEditedView(modified = modified)
    }

    @DeleteMapping("/{id}/dokumenttilknytninger/{journalpostId}/{dokumentInfoId}")
    fun removeTilknyttetDokument(
        @PathVariable("id") klagebehandlingId: UUID,
        @PathVariable("journalpostId") journalpostId: String,
        @PathVariable("dokumentInfoId") dokumentInfoId: String
    ): KlagebehandlingEditedView {
        logKlagebehandlingMethodDetails(
            "removeTilknyttetDokument", innloggetSaksbehandlerRepository.getInnloggetIdent(), klagebehandlingId,
            logger
        )
        val modified = klagebehandlingService.disconnectDokumentFromKlagebehandling(
            klagebehandlingId = klagebehandlingId,
            journalpostId = journalpostId,
            dokumentInfoId = dokumentInfoId,
            saksbehandlerIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return KlagebehandlingEditedView(modified)
    }
}