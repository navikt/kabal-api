package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.clients.regoppslag.RegoppslagClient
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import no.nav.klage.oppgave.util.TokenUtil
import org.springframework.stereotype.Service
import org.springframework.cache.annotation.Cacheable

@Service
class RegoppslagService(
    private val regoppslagClient: RegoppslagClient,
    private val tokenUtil: TokenUtil,
) {

    @Cacheable(CacheWithJCacheConfiguration.PERSON_ADDRESS)
    fun getAddressForPersonOnBehalfOf(fnr: String): BehandlingDetaljerView.Address? {
        val response = regoppslagClient.getMottakerOgAdresse(
            input = RegoppslagClient.Request(
                identifikator = fnr,
                type = RegoppslagClient.Request.RegoppslagType.PERSON
            ),
            token = tokenUtil.getOnBehalfOfTokenWithRegoppslagScope()
        )

        return getAddress(response)
    }

    @Cacheable(CacheWithJCacheConfiguration.PERSON_ADDRESS)
    fun getAddressForPersonAppAccess(fnr: String): BehandlingDetaljerView.Address? {
        val response = regoppslagClient.getMottakerOgAdresse(
            input = RegoppslagClient.Request(
                identifikator = fnr,
                type = RegoppslagClient.Request.RegoppslagType.PERSON
            ),
            token = tokenUtil.getAppAccessTokenWithRegoppslagScope()
        )

        return getAddress(response)
    }

    private fun getAddress(response: RegoppslagClient.HentMottakerOgAdresseResponse?) =
        if (response != null) {
            BehandlingDetaljerView.Address(
                adresselinje1 = response.adresse.adresselinje1 ?: "Mangler",
                adresselinje2 = response.adresse.adresselinje2,
                adresselinje3 = response.adresse.adresselinje3,
                landkode = response.adresse.landkode ?: "Mangler",
                postnummer = response.adresse.postnummer,
                poststed = response.adresse.poststed ?: "Mangler",
            )
        } else null
}