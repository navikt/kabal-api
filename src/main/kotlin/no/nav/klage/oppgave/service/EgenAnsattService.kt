package no.nav.klage.oppgave.service
import no.nav.klage.oppgave.clients.skjermedepersonerpip.SkjermedePersonerPipRestClient
import no.nav.klage.oppgave.config.CacheWithJCacheConfiguration
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class EgenAnsattService(
    private val skjermedePersonerPipRestClient: SkjermedePersonerPipRestClient
) {
    @Cacheable(CacheWithJCacheConfiguration.PERSON_IS_SKJERMET_CACHE)
    fun erEgenAnsatt(foedselsnr: String): Boolean =
        skjermedePersonerPipRestClient.personIsSkjermet(fnr = foedselsnr)
}