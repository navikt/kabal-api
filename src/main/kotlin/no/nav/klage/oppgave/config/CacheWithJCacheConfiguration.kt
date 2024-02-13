package no.nav.klage.oppgave.config


import no.nav.klage.oppgave.util.getLogger
import org.springframework.boot.autoconfigure.cache.JCacheManagerCustomizer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.util.concurrent.TimeUnit
import javax.cache.CacheManager
import javax.cache.configuration.MutableConfiguration
import javax.cache.expiry.CreatedExpiryPolicy
import javax.cache.expiry.Duration

@EnableCaching
@Configuration
class CacheWithJCacheConfiguration(private val environment: Environment) : JCacheManagerCustomizer {

    companion object {

        const val ENHET_CACHE = "enhet"
        private const val TILGANGER_CACHE = "tilganger"
        private const val ROLLER_CACHE = "roller"
        private const val SAKSBEHANDLERE_I_ENHET_CACHE = "saksbehandlereienhet"
        const val POSTSTEDER_CACHE = "poststeder"
        const val LANDKODER_CACHE = "landkoder"
        const val ANSATTE_I_ENHET_CACHE = "ansatteienhet"
        const val GROUPMEMBERS_CACHE = "groupmembers"
        const val KRR_INFO_CACHE = "krrinfo"
        const val SAKSBEHANDLER_NAME_CACHE = "saksbehandler-name"
        const val PERSON_ADDRESS = "person-address"


        val cacheKeys =
            listOf(
                ENHET_CACHE,
                TILGANGER_CACHE,
                ROLLER_CACHE,
                SAKSBEHANDLERE_I_ENHET_CACHE,
                GROUPMEMBERS_CACHE,
                KRR_INFO_CACHE,
                SAKSBEHANDLER_NAME_CACHE,
                POSTSTEDER_CACHE,
                LANDKODER_CACHE,
                PERSON_ADDRESS,
            )

        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    override fun customize(cacheManager: CacheManager) {
        cacheKeys.forEach { cacheName ->
            //Always cache for a long time.
            if (cacheName == ENHET_CACHE) {
                cacheManager.createCache(cacheName, cacheConfiguration(Duration(TimeUnit.HOURS, 8L)))
            } else {
                cacheManager.createCache(cacheName, cacheConfiguration(standardDuration()))
            }
        }
    }

    private fun cacheConfiguration(duration: Duration) =
        MutableConfiguration<Any, Any>()
            .setExpiryPolicyFactory(CreatedExpiryPolicy.factoryOf(duration))
            .setStoreByValue(false)
            .setStatisticsEnabled(true)

    private fun standardDuration() =
        if (environment.activeProfiles.contains("prod-gcp")) {
            Duration(TimeUnit.HOURS, 8L)
        } else {
            Duration(TimeUnit.MINUTES, 10L)
        }

}
