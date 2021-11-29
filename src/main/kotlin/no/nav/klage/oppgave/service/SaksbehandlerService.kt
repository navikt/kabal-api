package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.MedunderskrivereView
import no.nav.klage.oppgave.api.view.SaksbehandlerView
import no.nav.klage.oppgave.domain.klage.PartId
import no.nav.klage.oppgave.domain.kodeverk.PartIdType
import no.nav.klage.oppgave.domain.kodeverk.Ytelse
import no.nav.klage.oppgave.domain.kodeverk.enheterPerYtelse
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
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getEnheterMedYtelserForSaksbehandler(): EnheterMedLovligeYtelser =
        innloggetSaksbehandlerRepository.getEnheterMedYtelserForSaksbehandler()

    fun getEnheterForSaksbehandler(): List<Enhet> =
        innloggetSaksbehandlerRepository.getEnheterForSaksbehandler()

    fun getEnheterForSaksbehandler(navIdent: String): List<Enhet> =
        saksbehandlerRepository.getEnheterForSaksbehandler(navIdent)

    fun getMedunderskrivere(navIdent: String, enhetId: String, ytelse: Ytelse): MedunderskrivereView =
        if (enheterPerYtelse.contains(ytelse)) {
            val medunderskrivere = enheterPerYtelse[ytelse]!!
                .flatMap { enhetRepository.getAnsatteIEnhet(it) }
                .filter { it != navIdent }
                .filter { saksbehandlerRepository.erSaksbehandler(it) }
                .distinct()
                .map { SaksbehandlerView(it, getNameForIdent(it)) }
            MedunderskrivereView(tema = null, ytelse = ytelse.id, medunderskrivere = medunderskrivere)
        } else {
            logger.error("Ytelsen $ytelse har ingen registrerte enheter i systemet vårt")
            MedunderskrivereView(tema = null, ytelse = ytelse.id, medunderskrivere = emptyList())
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
