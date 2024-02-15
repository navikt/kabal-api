package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.clients.regoppslag.RegoppslagClient
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import org.springframework.stereotype.Service
import org.springframework.cache.annotation.Cacheable

@Service
class RegoppslagService(
    private val regoppslagClient: RegoppslagClient,
) {

    @Cacheable(CacheWithJCacheConfiguration.PERSON_ADDRESS)
    fun getAddressForPerson(fnr: String): BehandlingDetaljerView.Address {
        val response = regoppslagClient.getMottakerOgAdresse(
            input = RegoppslagClient.Request(
                identifikator = fnr,
                type = RegoppslagClient.Request.RegoppslagType.PERSON
            )
        )

        return BehandlingDetaljerView.Address(
            adresselinje1 = response.adresse.adresselinje1 ?: "Mangler",
            adresselinje2 = response.adresse.adresselinje2,
            adresselinje3 = response.adresse.adresselinje3,
            landkode = response.adresse.landkode ?: "Mangler",
            postnummer = response.adresse.postnummer,
            poststed = response.adresse.poststed ?: "Mangler",
        )
    }
}