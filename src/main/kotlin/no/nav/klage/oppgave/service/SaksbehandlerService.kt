package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Ytelse
import no.nav.klage.kodeverk.ytelseTilKlageenheter
import no.nav.klage.oppgave.api.view.Medunderskriver
import no.nav.klage.oppgave.api.view.Medunderskrivere
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.pdl.PdlFacade
import no.nav.klage.oppgave.domain.klage.PartId
import no.nav.klage.oppgave.domain.saksbehandler.Enhet
import no.nav.klage.oppgave.domain.saksbehandler.EnheterMedLovligeYtelser
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.repositories.EnhetRepository
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.repositories.SaksbehandlerRepository
import no.nav.klage.oppgave.util.getLogger
import org.springframework.stereotype.Service

@Service
class SaksbehandlerService(
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val tilgangService: TilgangService,
    private val azureGateway: AzureGateway,
    private val enhetRepository: EnhetRepository,
    private val pdlFacade: PdlFacade,
    private val egenAnsattService: EgenAnsattService,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        const val VIKAFOSSEN = "2103"
    }

    fun getEnheterMedYtelserForSaksbehandler(): EnheterMedLovligeYtelser =
        innloggetSaksbehandlerRepository.getEnheterMedYtelserForSaksbehandler()

    fun getEnheterForSaksbehandler(): List<Enhet> =
        innloggetSaksbehandlerRepository.getEnheterForSaksbehandler()

    fun getEnheterForSaksbehandler(navIdent: String): List<Enhet> =
        saksbehandlerRepository.getEnheterForSaksbehandler(navIdent)

    fun getMedunderskrivere(ident: String, enhetId: String, ytelse: Ytelse, fnr: String? = null): Medunderskrivere =
        if (fnr != null) {
            val personInfo = pdlFacade.getPersonInfo(fnr)
            val harBeskyttelsesbehovFortrolig = personInfo.harBeskyttelsesbehovFortrolig()
            val harBeskyttelsesbehovStrengtFortrolig = personInfo.harBeskyttelsesbehovStrengtFortrolig()
            val erEgenAnsatt = egenAnsattService.erEgenAnsatt(fnr)

            if (harBeskyttelsesbehovStrengtFortrolig) {
                //Kode 6 har skal ikke ha medunderskrivere
                Medunderskrivere(null, ytelse.id, emptyList())
            }
            if (harBeskyttelsesbehovFortrolig) {
                //Kode 7 skal ha medunderskrivere fra alle ytelser, men kun de med kode 7-rettigheter
                //TODO: Dette er klønete, vi burde gjort dette i ETT kall til AD, ikke n.
                val medunderskrivere = saksbehandlerRepository.getSaksbehandlereSomKanBehandleFortrolig()
                    .filter { it != ident }
                    .filter { egenAnsattFilter(fnr, erEgenAnsatt, ident) }
                    .map { Medunderskriver(it, it, getNameForIdent(it)) }
                Medunderskrivere(tema = null, ytelse = ytelse.id, medunderskrivere = medunderskrivere)
            }
            if (ytelseTilKlageenheter.contains(ytelse)) {
                val medunderskrivere = ytelseTilKlageenheter[ytelse]!!
                    .filter { it.navn != VIKAFOSSEN }
                    .sortedBy { it.navn != enhetId }
                    .flatMap { enhetRepository.getAnsatteIEnhet(it.navn) }
                    .filter { it != ident }
                    .filter { saksbehandlerRepository.erSaksbehandler(it) }
                    .filter { egenAnsattFilter(fnr, erEgenAnsatt, ident) }
                    .distinct()
                    .map { Medunderskriver(it, it, getNameForIdent(it)) }
                Medunderskrivere(tema = null, ytelse = ytelse.id, medunderskrivere = medunderskrivere)
            } else {
                logger.error("Ytelsen $ytelse har ingen registrerte enheter i systemet vårt")
                Medunderskrivere(tema = null, ytelse = ytelse.id, medunderskrivere = emptyList())
            }

        } else {
            if (ytelseTilKlageenheter.contains(ytelse)) {
                val medunderskrivere = ytelseTilKlageenheter[ytelse]!!
                    .filter { it.navn != VIKAFOSSEN }
                    .sortedBy { it.navn != enhetId }
                    .flatMap { enhetRepository.getAnsatteIEnhet(it.navn) }
                    .filter { it != ident }
                    .filter { saksbehandlerRepository.erSaksbehandler(it) }
                    .distinct()
                    .map { Medunderskriver(it, it, getNameForIdent(it)) }
                Medunderskrivere(tema = null, ytelse = ytelse.id, medunderskrivere = medunderskrivere)
            } else {
                logger.error("Ytelsen $ytelse har ingen registrerte enheter i systemet vårt")
                Medunderskrivere(tema = null, ytelse = ytelse.id, medunderskrivere = emptyList())
            }
        }

    private fun egenAnsattFilter(fnr: String, erEgenAnsatt: Boolean, ident: String) =
        if (!erEgenAnsatt) {
            true
        } else {
            tilgangService.harSaksbehandlerTilgangTil(ident = ident, fnr = fnr)
        }

    private fun saksbehandlerHarTilgangTilPerson(ident: String, partId: PartId): Boolean =
        if (partId.type == PartIdType.VIRKSOMHET) {
            true
        } else {
            tilgangService.harSaksbehandlerTilgangTil(ident, partId.value)
        }

    private fun saksbehandlerHarTilgangTilYtelse(ident: String, ytelse: Ytelse) =
        saksbehandlerRepository.getEnheterMedYtelserForSaksbehandler(ident).enheter.flatMap { it.ytelser }
            .contains(ytelse)

    fun getNameForIdent(it: String) =
        saksbehandlerRepository.getNamesForSaksbehandlere(setOf(it)).getOrDefault(it, "Ukjent navn")

    fun getNamesForSaksbehandlere(idents: Set<String>): Map<String, String> =
        saksbehandlerRepository.getNamesForSaksbehandlere(idents)

}
