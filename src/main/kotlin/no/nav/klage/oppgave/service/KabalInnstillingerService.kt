package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.hjemmel.Hjemmel
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.api.view.MedunderskrivereWithEnhetView
import no.nav.klage.oppgave.api.view.SaksbehandlerWithEnhetView
import no.nav.klage.oppgave.api.view.SaksbehandlereWithEnhetView
import no.nav.klage.oppgave.clients.kabalinnstillinger.KabalInnstillingerClient
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.MedunderskrivereInput
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.SakInput
import no.nav.klage.oppgave.clients.kabalinnstillinger.model.Saksbehandlere
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupGateway
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.util.getLogger
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class KabalInnstillingerService(
    private val kabalInnstillingerClient: KabalInnstillingerClient,
    private val klageLookupGateway: KlageLookupGateway,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    fun getPotentialSaksbehandlere(behandling: Behandling): SaksbehandlereWithEnhetView {
        val foundSaksbehandlere = kabalInnstillingerClient.searchSaksbehandlere(
            SakInput(
                ytelseId = behandling.ytelse.id,
                fnr = behandling.sakenGjelder.partId.value,
                sakId = behandling.fagsakId,
                fagsystemId = behandling.fagsystem.id,
            )
        )

        return SaksbehandlereWithEnhetView(
            saksbehandlere = foundSaksbehandlere.saksbehandlere.map {
                SaksbehandlerWithEnhetView(
                    navIdent = it.navIdent,
                    navn = it.navn,
                    ansattEnhetId = klageLookupGateway.getUserInfoForGivenNavIdent(navIdent = it.navIdent).enhet.enhetId,
                )
            }
        )
    }

    fun getPotentialMedunderskrivere(behandling: Behandling): MedunderskrivereWithEnhetView {
        if (behandling.tildeling == null) {
            return MedunderskrivereWithEnhetView(medunderskrivere = emptyList())
        }
        val foundMedunderskrivere = kabalInnstillingerClient.searchMedunderskrivere(
            MedunderskrivereInput(
                enhet = behandling.tildeling!!.enhet!!,
                navIdent = behandling.tildeling!!.saksbehandlerident!!,
                sak = SakInput(
                    fnr = behandling.sakenGjelder.partId.value,
                    sakId = behandling.fagsakId,
                    fagsystemId = behandling.fagsystem.id,
                    ytelseId = behandling.ytelse.id,
                )
            )
        )

        return MedunderskrivereWithEnhetView(
            medunderskrivere = foundMedunderskrivere.medunderskrivere.map {
                SaksbehandlerWithEnhetView(
                    navIdent = it.navIdent,
                    navn = it.navn,
                    ansattEnhetId = klageLookupGateway.getUserInfoForGivenNavIdent(navIdent = it.navIdent).enhet.enhetId,
                )
            }
        )
    }

    fun getPotentialROL(behandling: Behandling): Saksbehandlere {
        return kabalInnstillingerClient.searchROL(
            SakInput(
                fnr = behandling.sakenGjelder.partId.value,
                sakId = behandling.fagsakId,
                fagsystemId = behandling.fagsystem.id,
                ytelseId = behandling.ytelse.id,
            )
        )
    }

    //TODO: Bør vi ha et cache her? Kan være et problem om leder gir nye tilganger, kanskje et kortere cache?
    fun getTildelteYtelserForSaksbehandler(navIdent: String): List<Ytelse> {
        return kabalInnstillingerClient.getSaksbehandlersTildelteYtelser(navIdent).ytelseIdList.map {
            Ytelse.of(it)
        }
    }

    fun getTildelteYtelserForEnhet(enhet: String): Set<Ytelse> {
        return kabalInnstillingerClient.getTildelteYtelserForEnhet(enhet).ytelseIdList.map {
            Ytelse.of(it)
        }.toSet()
    }

    @Cacheable(CacheWithJCacheConfiguration.HJEMLER_FOR_YTELSE_CACHE)
    fun getRegisteredHjemlerForYtelse(ytelse: Ytelse): Set<Hjemmel> {
        val hjemler = kabalInnstillingerClient.getHjemmelIdsForYtelse(ytelse)
        return hjemler.map { Hjemmel.of(it) }.toSet()
    }
}