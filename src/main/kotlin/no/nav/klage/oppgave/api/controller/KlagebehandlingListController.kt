package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import no.nav.klage.oppgave.api.mapper.KlagebehandlingListMapper
import no.nav.klage.oppgave.api.mapper.KlagebehandlingerSearchCriteriaMapper
import no.nav.klage.oppgave.api.view.*
import no.nav.klage.oppgave.config.SecurityConfiguration.Companion.ISSUER_AAD
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.exceptions.NotMatchingUserException
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.service.ElasticsearchService
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.service.PersonsoekService
import no.nav.klage.oppgave.service.SaksbehandlerService
import no.nav.klage.oppgave.util.getLogger
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@Api(tags = ["kabal-api"])
@ProtectedWithClaims(issuer = ISSUER_AAD)
//TODO: Er det litt merkelig med "ansatte" på rot i path her?
@RequestMapping("/ansatte")
class KlagebehandlingListController(
    private val klagebehandlingService: KlagebehandlingService,
    private val klagebehandlingMapper: KlagebehandlingListMapper,
    private val elasticsearchService: ElasticsearchService,
    private val klagebehandlingerSearchCriteriaMapper: KlagebehandlingerSearchCriteriaMapper,
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val saksbehandlerService: SaksbehandlerService,
    private val personsoekService: PersonsoekService
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @ApiOperation(
        value = "Hent oppgaver for en ansatt",
        notes = "Henter alle oppgaver som saksbehandler har tilgang til."
    )
    @GetMapping("/{navIdent}/klagebehandlinger", produces = ["application/json"])
    fun getOppgaver(
        @ApiParam(value = "NavIdent til en ansatt")
        @PathVariable navIdent: String,
        queryParams: KlagebehandlingerQueryParams
    ): KlagebehandlingerListRespons {
        logger.debug("Params: {}", queryParams)
        validateNavIdent(navIdent)

        validateRettigheter(queryParams, navIdent)

        val valgtEnhet = saksbehandlerService.findValgtEnhet(innloggetSaksbehandlerRepository.getInnloggetIdent())
        val searchCriteria = if (queryParams.temaer.isEmpty()) {
            klagebehandlingerSearchCriteriaMapper.toSearchCriteria(
                navIdent,
                queryParams.copy(temaer = valgtEnhet.temaer.map { it.id }),
                valgtEnhet
            )
        } else {
            klagebehandlingerSearchCriteriaMapper.toSearchCriteria(navIdent, queryParams, valgtEnhet)
        }


        val esResponse = elasticsearchService.findByCriteria(searchCriteria)
        return KlagebehandlingerListRespons(
            antallTreffTotalt = esResponse.totalHits.toInt(),
            klagebehandlinger = klagebehandlingMapper.mapEsKlagebehandlingerToListView(
                esResponse.searchHits.map { it.content },
                searchCriteria.isProjectionUtvidet(),
                searchCriteria.ferdigstiltFom != null,
                searchCriteria.saksbehandler,
                valgtEnhet.temaer
            )
        )
    }

    /*
        Does user have the rights to get all tildelte oppgaver?
     */
    private fun validateRettigheter(
        queryParams: KlagebehandlingerQueryParams,
        navIdent: String
    ) {
        if (queryParams.erTildeltSaksbehandler == true && queryParams.tildeltSaksbehandler == null) {
            if (!canSeeTildelteOppgaver()) {
                val message = "$navIdent har ikke tilgang til å se alle tildelte oppgaver."
                logger.warn(message)
                throw MissingTilgangException(message)
            }
        }
    }

    private fun canSeeTildelteOppgaver() = innloggetSaksbehandlerRepository.erLeder() ||
            innloggetSaksbehandlerRepository.erFagansvarlig()

    @ApiOperation(
        value = "Hent oppgaver som gjelder en gitt person",
        notes = "Henter alle oppgaver som saksbehandler har tilgang til som omhandler en gitt person."
    )
    @PostMapping("/{navIdent}/klagebehandlinger/personsoek", produces = ["application/json"])
    fun getOppgaverOmPerson(
        @ApiParam(value = "NavIdent til en ansatt")
        @PathVariable navIdent: String,
        @RequestBody input: PersonSoekInput
    ): KlagebehandlingerPersonSoekListRespons {
        validateNavIdent(navIdent)

        val searchCriteria = klagebehandlingerSearchCriteriaMapper.toSearchCriteria(navIdent, input)
        val personsoekResponse = personsoekService.personsoek(searchCriteria)
        val saksbehandler = innloggetSaksbehandlerRepository.getInnloggetIdent()
        val valgtEnhet = saksbehandlerService.findValgtEnhet(saksbehandler)
        return KlagebehandlingerPersonSoekListRespons(
            antallTreffTotalt = personsoekResponse.liste.size,
            personer = klagebehandlingMapper.mapPersonSoekResponseToPersonSoekListView(
                personSoekResponse = personsoekResponse,
                viseUtvidet = searchCriteria.isProjectionUtvidet(),
                saksbehandler = saksbehandler,
                tilgangTilTemaer = valgtEnhet.temaer
            )
        )
    }

    @PostMapping("/{navIdent}/klagebehandlinger/{id}/saksbehandlertildeling")
    fun assignSaksbehandler(
        @ApiParam(value = "NavIdent til en ansatt")
        @PathVariable navIdent: String,
        @ApiParam(value = "Id til en klagebehandling")
        @PathVariable("id") klagebehandlingId: UUID,
        @RequestBody saksbehandlertildeling: Saksbehandlertildeling
    ): TildelingEditedView {
        logger.debug("assignSaksbehandler is requested for klagebehandling: {}", klagebehandlingId)
        val klagebehandling = klagebehandlingService.assignKlagebehandling(
            klagebehandlingId,
            saksbehandlertildeling.klagebehandlingVersjon,
            saksbehandlertildeling.navIdent,
            saksbehandlertildeling.angittEnhetOrDefault(),
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )
        return TildelingEditedView(
            klagebehandling.versjon,
            klagebehandling.modified,
            klagebehandling.tildeling!!.tidspunkt.toLocalDate()
        )
    }

    @PostMapping("/{navIdent}/klagebehandlinger/{id}/saksbehandlerfradeling")
    fun unassignSaksbehandler(
        @ApiParam(value = "NavIdent til en ansatt")
        @PathVariable navIdent: String,
        @ApiParam(value = "Id til en klagebehandling")
        @PathVariable("id") klagebehandlingId: UUID,
        @RequestBody(required = false) saksbehandlerfradeling: Saksbehandlerfradeling?
    ): TildelingEditedView {
        logger.debug("unassignSaksbehandler is requested for klagebehandling: {}", klagebehandlingId)
        val klagebehandling = klagebehandlingService.assignKlagebehandling(
            klagebehandlingId,
            saksbehandlerfradeling?.klagebehandlingVersjon,
            null,
            null,
            innloggetSaksbehandlerRepository.getInnloggetIdent()
        )

        return TildelingEditedView(
            klagebehandling.versjon,
            klagebehandling.modified,
            klagebehandling.tildeling!!.tidspunkt.toLocalDate()
        )
    }

    @ApiOperation(
        value = "Hent antall utildelte klagebehandlinger der fristen gått ut",
        notes = "Teller opp alle utildelte klagebehandlinger der fristen gått ut."
    )
    @GetMapping("/{navIdent}/antallklagebehandlingermedutgaattefrister", produces = ["application/json"])
    fun getAntallUtgaatteFrister(
        @ApiParam(value = "NavIdent til en ansatt")
        @PathVariable navIdent: String,
        queryParams: KlagebehandlingerQueryParams
    ): AntallUtgaatteFristerResponse {
        logger.debug("Params: {}", queryParams)
        validateNavIdent(navIdent)
        return AntallUtgaatteFristerResponse(
            antall = elasticsearchService.countByCriteria(
                klagebehandlingerSearchCriteriaMapper.toFristUtgaattIkkeTildeltSearchCriteria(
                    navIdent,
                    queryParams
                )
            )
        )
    }

    private fun validateNavIdent(navIdent: String) {
        val innloggetIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        if (innloggetIdent != navIdent) {
            throw NotMatchingUserException(
                "logged in user does not match sent in user. " +
                        "Logged in: $innloggetIdent, sent in: $navIdent"
            )
        }
    }

    // Vi har bestemt at det er greit å hente dette fra db, men jeg beholder muligheten her til å sende det inn fra frontend "just in case".. :)
    private fun Saksbehandlertildeling.angittEnhetOrDefault(): String =
        enhetId ?: saksbehandlerService.findValgtEnhet(innloggetSaksbehandlerRepository.getInnloggetIdent()).enhetId

    //    @PutMapping("/oppgaver/{id}/hjemmel")
//    fun setHjemmel(
//        @PathVariable("id") oppgaveId: String,
//        @RequestBody hjemmelUpdate: HjemmelUpdate
//    ): ResponseEntity<OppgaveView> {
//        logger.debug("setHjemmel is requested")
//        val oppgave =
//            oppgaveService.setHjemmel(oppgaveId.toLongOrException(), hjemmelUpdate.hjemmel, hjemmelUpdate.versjon)
//        val uri = MvcUriComponentsBuilder
//            .fromMethodName(OppgaveController::class.java, "getOppgave", oppgaveId)
//            .buildAndExpand(oppgaveId).toUri()
//        return ResponseEntity.ok().location(uri).body(oppgave)
//    }
//
}

