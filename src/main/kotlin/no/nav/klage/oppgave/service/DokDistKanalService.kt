package no.nav.klage.oppgave.service

import no.nav.klage.kodeverk.Tema
import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.clients.dokdistkanal.DokDistKanalClient
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class DokDistKanalService(
    private val dokDistKanalClient: DokDistKanalClient,
) {

    @Cacheable(CacheWithJCacheConfiguration.DOK_DIST_KANAL)
    fun getUtsendingskanal(
        mottakerId: String,
        brukerId: String,
        tema: Tema
    ): BehandlingDetaljerView.Utsendingskanal {
        val dokDistKanalResponse = dokDistKanalClient.getDistribusjonskanal(
            input = DokDistKanalClient.Request(
                mottakerId = mottakerId,
                brukerId = brukerId,
                tema = tema.navn
            )
        )

        return dokDistKanalResponse.distribusjonskanal.toUtsendingskanal()
    }

    private fun DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.toUtsendingskanal(): BehandlingDetaljerView.Utsendingskanal {
        return when (this) {
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.PRINT -> BehandlingDetaljerView.Utsendingskanal.SENTRAL_PRINT
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.SDP -> BehandlingDetaljerView.Utsendingskanal.DPI
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.DITT_NAV -> BehandlingDetaljerView.Utsendingskanal.NAV_NO
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.LOKAL_PRINT -> BehandlingDetaljerView.Utsendingskanal.LOKAL_PRINT
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.INGEN_DISTRIBUSJON -> BehandlingDetaljerView.Utsendingskanal.INGEN_DISTRIBUSJON
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.TRYGDERETTEN -> BehandlingDetaljerView.Utsendingskanal.TRYGDERETTEN
            DokDistKanalClient.BestemDistribusjonskanalResponse.DistribusjonKanalCode.DPVT -> BehandlingDetaljerView.Utsendingskanal.TPAM
        }
    }
}