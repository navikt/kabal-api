package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.Medunderskriver
import no.nav.klage.oppgave.api.view.Medunderskrivere
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.domain.klage.PartId
import no.nav.klage.oppgave.domain.kodeverk.PartIdType
import no.nav.klage.oppgave.domain.kodeverk.Tema
import no.nav.klage.oppgave.domain.saksbehandler.*
import no.nav.klage.oppgave.exceptions.MissingTilgangException
import no.nav.klage.oppgave.gateway.AzureGateway
import no.nav.klage.oppgave.repositories.InnloggetSaksbehandlerRepository
import no.nav.klage.oppgave.repositories.InnstillingerRepository
import no.nav.klage.oppgave.repositories.SaksbehandlerRepository
import no.nav.klage.oppgave.repositories.ValgtEnhetRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class SaksbehandlerService(
    private val innloggetSaksbehandlerRepository: InnloggetSaksbehandlerRepository,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val valgtEnhetRepository: ValgtEnhetRepository,
    private val innstillingerRepository: InnstillingerRepository,
    private val tilgangService: TilgangService,
    private val azureGateway: AzureGateway
) {
    fun getEnheterMedTemaerForSaksbehandler(): EnheterMedLovligeTemaer =
        innloggetSaksbehandlerRepository.getEnheterMedTemaerForSaksbehandler()

    fun getMedunderskrivere(innloggetSaksbehandlerident: String, klagebehandling: Klagebehandling): Medunderskrivere {
        val ident = klagebehandling.tildeling?.saksbehandlerident ?: innloggetSaksbehandlerident
        val medunderskrivere = saksbehandlerRepository.getAlleSaksbehandlerIdenter()
            .filter { it != ident }
            .filter { saksbehandlerHarTilgangTilTema(it, klagebehandling.ytelse.toTema()) }
            .filter { saksbehandlerHarTilgangTilPerson(it, klagebehandling.sakenGjelder.partId) }
            .map { Medunderskriver(it, getNameForIdent(it)) }
        return Medunderskrivere(klagebehandling.ytelse.toTema().id, medunderskrivere)
    }

    fun getMedunderskrivere(ident: String, tema: Tema): Medunderskrivere {
        val medunderskrivere = saksbehandlerRepository.getAlleSaksbehandlerIdenter()
            .filter { it != ident }
            .filter { saksbehandlerHarTilgangTilTema(it, tema) }
            .map { Medunderskriver(it, getNameForIdent(it)) }
        return Medunderskrivere(tema.id, medunderskrivere)
    }

    private fun saksbehandlerHarTilgangTilPerson(ident: String, partId: PartId): Boolean =
        if (partId.type == PartIdType.VIRKSOMHET) {
            true
        } else {
            tilgangService.harSaksbehandlerTilgangTil(ident, partId.value)
        }

    private fun saksbehandlerHarTilgangTilTema(ident: String, tema: Tema) =
        saksbehandlerRepository.getEnheterMedTemaerForSaksbehandler(ident).enheter.flatMap { it.temaer }
            .contains(tema)

    fun getNameForIdent(it: String) =
        saksbehandlerRepository.getNamesForSaksbehandlere(setOf(it)).getOrDefault(it, "Ukjent navn")

    fun getNamesForSaksbehandlere(idents: Set<String>): Map<String, String> =
        saksbehandlerRepository.getNamesForSaksbehandlere(idents)

    @Transactional
    fun storeValgtEnhetId(ident: String, enhetId: String): EnhetMedLovligeTemaer {
        val enhet = getEnheterMedTemaerForSaksbehandler().enheter.find { it.enhetId == enhetId }
            ?: throw MissingTilgangException("Saksbehandler $ident har ikke tilgang til enhet $enhetId")

        valgtEnhetRepository.save(
            mapToValgtEnhet(ident, enhet)
        )
        return enhet
    }

    private fun mapToValgtEnhet(ident: String, enhet: EnhetMedLovligeTemaer): ValgtEnhet {
        return ValgtEnhet(
            saksbehandlerident = ident,
            enhetId = enhet.enhetId,
            enhetNavn = enhet.navn,
            tidspunkt = LocalDateTime.now()
        )
    }

    @Transactional
    fun findValgtEnhet(ident: String): EnhetMedLovligeTemaer {
        return valgtEnhetRepository.findByIdOrNull(ident)
            ?.let { valgtEnhet -> getEnheterMedTemaerForSaksbehandler().enheter.find { it.enhetId == valgtEnhet.enhetId } }
            ?: storeValgtEnhetId(ident, getEnheterMedTemaerForSaksbehandler().enheter.first().enhetId)
    }

    @Transactional
    fun findInnstillinger(ident: String): SaksbehandlerInnstillinger {
        return innstillingerRepository.findByIdOrNull(ident)?.toSaksbehandlerInnstillinger()
            ?: SaksbehandlerInnstillinger()
    }

    @Transactional
    fun storeInnstillinger(
        navIdent: String,
        saksbehandlerInnstillinger: SaksbehandlerInnstillinger
    ): SaksbehandlerInnstillinger {
        return innstillingerRepository.save(
            Innstillinger.fromSaksbehandlersInnstillinger(
                navIdent,
                saksbehandlerInnstillinger
            )
        ).toSaksbehandlerInnstillinger()
    }

    fun getDataOmSaksbehandler(navIdent: String): SaksbehandlerInfo {
        val dataOmInnloggetSaksbehandler = azureGateway.getDataOmInnloggetSaksbehandler()
        azureGateway.getRollerForInnloggetSaksbehandler()
        val rollerForInnloggetSaksbehandler =
            azureGateway.getRollerForSaksbehandlerMedIdent(innloggetSaksbehandlerRepository.getInnloggetIdent())
        val enheterForInnloggetSaksbehandler = innloggetSaksbehandlerRepository.getEnheterMedTemaerForSaksbehandler()
        val valgtEnhet = findValgtEnhet(innloggetSaksbehandlerRepository.getInnloggetIdent())
        val innstillinger = findInnstillinger(innloggetSaksbehandlerRepository.getInnloggetIdent())
        return SaksbehandlerInfo(
            dataOmInnloggetSaksbehandler,
            rollerForInnloggetSaksbehandler,
            enheterForInnloggetSaksbehandler,
            valgtEnhet,
            innstillinger
        )
    }
}
