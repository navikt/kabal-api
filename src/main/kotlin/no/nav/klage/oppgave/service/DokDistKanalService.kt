package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.clients.dokdistkanal.DokDistKanalClient
import no.nav.klage.oppgave.clients.ereg.EregClient
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import no.nav.klage.oppgave.util.getPartIdFromIdentifikator
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class DokDistKanalService(
    private val dokDistKanalClient: DokDistKanalClient,
    private val eregClient: EregClient,
) {
    @Cacheable(CacheWithJCacheConfiguration.DOK_DIST_KANAL)
    fun getUtsendingskanal(
        mottakerId: String,
        brukerId: String,
        tema: Tema,
    ): BehandlingDetaljerView.Utsendingskanal {
        return getDistribusjonKanalCode(
            mottakerId = mottakerId,
            brukerId = brukerId,
            tema = tema
        ).toBehandlingDetaljerViewUtsendingskanal()
    }

    fun getDistribusjonKanalCode(
        mottakerId: String,
        brukerId: String,
        tema: Tema,
    ): DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode {
        val isOrganisasjon = getPartIdFromIdentifikator(mottakerId).type == PartIdType.VIRKSOMHET

        if (isOrganisasjon) {
            val noekkelInfoOmOrganisasjon = eregClient.hentNoekkelInformasjonOmOrganisasjon(mottakerId)
            //Override value for DELT_ANSVAR
            if (noekkelInfoOmOrganisasjon.isDeltAnsvar()) {
                return DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.PRINT
            }
        }

        val dokDistKanalResponse = dokDistKanalClient.getDistribusjonskanal(
            input = DokDistKanalClient.Request(
                mottakerId = mottakerId,
                brukerId = brukerId,
                tema = tema.navn
            )
        )

        return dokDistKanalResponse.distribusjonskanal
    }

    private fun DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.toBehandlingDetaljerViewUtsendingskanal(): BehandlingDetaljerView.Utsendingskanal {
        return when (this) {
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.PRINT -> BehandlingDetaljerView.Utsendingskanal.SENTRAL_UTSKRIFT
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.SDP -> BehandlingDetaljerView.Utsendingskanal.SDP
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.DITT_NAV -> BehandlingDetaljerView.Utsendingskanal.NAV_NO
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.LOKAL_PRINT -> BehandlingDetaljerView.Utsendingskanal.LOKAL_UTSKRIFT
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.INGEN_DISTRIBUSJON -> BehandlingDetaljerView.Utsendingskanal.INGEN_DISTRIBUSJON
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.TRYGDERETTEN -> BehandlingDetaljerView.Utsendingskanal.TRYGDERETTEN
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.DPVT -> BehandlingDetaljerView.Utsendingskanal.DPVT
        }
    }
}