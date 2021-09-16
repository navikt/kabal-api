package no.nav.klage.oppgave.api.controller

import io.swagger.annotations.Api
import no.nav.klage.oppgave.api.mapper.KlagebehandlingListMapper
import no.nav.klage.oppgave.api.view.KlagebehandlingerListRespons
import no.nav.klage.oppgave.api.view.Medunderskriver
import no.nav.klage.oppgave.api.view.Medunderskrivere
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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

    @GetMapping("/{id}/muligemedunderskrivere")
    fun getPossibleMedunderskrivere(
        @PathVariable("id") klagebehandlingId: UUID
    ): Medunderskrivere {
        val navIdent = innloggetSaksbehandlerRepository.getInnloggetIdent()
        logger.debug("getPossibleMedunderskrivere is requested by $navIdent")
        val klagebehandling = klagebehandlingService.getKlagebehandlingForUpdate(klagebehandlingId)
        val tema = klagebehandling.tema
        return if (environment.activeProfiles.contains("prod-gcp")) {
            saksbehandlerService.getMedunderskrivere(navIdent, klagebehandling)
        } else Medunderskrivere(
            tema.id,
            listOf(
                Medunderskriver("Z994488", "F_Z994488, E_Z994488"),
                Medunderskriver("Z994330", "F_Z994330 E_Z994330"),
                Medunderskriver("Z994861", "F_Z994861 E_Z994861"),
                Medunderskriver("Z994864", "F_Z994864 E_Z994864"),
                Medunderskriver("Z994863", "F_Z994863 E_Z994863"),
                Medunderskriver("Z994862", "F_Z994862 E_Z994862"),
            ).filter { it.ident != navIdent }
        )
    }

    @GetMapping("/{id}/relaterte")
    fun getRelaterteKlagebehandlinger(
        @PathVariable("id") klagebehandlingId: UUID
    ): KlagebehandlingerListRespons {
        logKlagebehandlingMethodDetails("getRelaterteKlagebehandlinger", innloggetSaksbehandlerRepository.getInnloggetIdent(), klagebehandlingId, logger)
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
}