package no.nav.klage.oppgave.service

import no.nav.klage.oppgave.api.view.BehandlingDetaljerView
import no.nav.klage.oppgave.clients.klagelookup.KlageLookupClient
import no.nav.klage.oppgave.clients.klagelookup.PostadresseResponse
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class RegoppslagService(
    private val klageLookupClient: KlageLookupClient,
) {

    @Cacheable(CacheWithJCacheConfiguration.PERSON_ADDRESS)
    fun getAddressForPerson(fnr: String): BehandlingDetaljerView.Address? {
        val response = klageLookupClient.getPostadresse(ident = fnr)
        return getAddress(response)
    }

    private fun getAddress(response: PostadresseResponse): BehandlingDetaljerView.Address? {
        return BehandlingDetaljerView.Address(
            adresselinje1 = response.adresse?.adresselinje1 ?: "Mangler",
            adresselinje2 = response.adresse?.adresselinje2,
            adresselinje3 = response.adresse?.adresselinje3,
            landkode = response.adresse?.landkode ?: "Mangler",
            postnummer = response.adresse?.postnummer,
            poststed = response.adresse?.poststed ?: "Mangler",
        )
    }
}