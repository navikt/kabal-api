package no.nav.klage.oppgave.config


import no.nav.klage.oppgave.util.getLogger
import org.springframework.boot.cache.autoconfigure.JCacheManagerCustomizer
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
        const val ENHETER_CACHE = "enheter"
        const val POSTSTEDER_CACHE = "poststeder"
        const val LANDKODER_CACHE = "landkoder"
        const val KRR_INFO_CACHE = "krrinfo"
        const val HJEMLER_FOR_YTELSE_CACHE = "hjemler-for-ytelse"
        const val SAKSBEHANDLER_NAME_CACHE = "saksbehandler-name"
        const val PERSON_ADDRESS = "person-address"
        const val DOK_DIST_KANAL = "dok-dist-kanal"
        const val GOSYSOPPGAVE_GJELDER_CACHE = "gosysoppgave-gjelder"
        const val GOSYSOPPGAVE_OPPGAVETYPE_CACHE = "gosysoppgave-oppgavetype"
        const val GOSYSOPPGAVE_ENHETSMAPPER_CACHE = "gosysoppgave-enhetmapper"
        const val GOSYSOPPGAVE_ENHETSMAPPE_CACHE = "gosysoppgave-enhetmappe"


        val cacheKeys =
            listOf(
                ENHET_CACHE,
                ENHETER_CACHE,
                KRR_INFO_CACHE,
                SAKSBEHANDLER_NAME_CACHE,
                POSTSTEDER_CACHE,
                LANDKODER_CACHE,
                PERSON_ADDRESS,
                DOK_DIST_KANAL,
                GOSYSOPPGAVE_GJELDER_CACHE,
                GOSYSOPPGAVE_OPPGAVETYPE_CACHE,
                GOSYSOPPGAVE_ENHETSMAPPER_CACHE,
                GOSYSOPPGAVE_ENHETSMAPPE_CACHE,
                HJEMLER_FOR_YTELSE_CACHE,
            )

        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    override fun customize(cacheManager: CacheManager) {
        cacheKeys.forEach { cacheName ->
            //Always cache for a long time.
            if (cacheName in listOf(ENHET_CACHE, ENHETER_CACHE)) {
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
        if (environment.activeProfiles.contains("prod")) {
            Duration(TimeUnit.HOURS, 8L)
        } else {
            Duration(TimeUnit.MINUTES, 10L)
        }

}