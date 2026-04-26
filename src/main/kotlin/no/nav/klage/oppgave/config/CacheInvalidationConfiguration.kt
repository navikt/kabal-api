package no.nav.klage.oppgave.config

import no.nav.klage.oppgave.util.getLogger
import org.springframework.cache.CacheManager
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener

@Configuration
class CacheInvalidationConfiguration(val cacheManager: CacheManager) :
    ApplicationListener<ContextRefreshedEvent> {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @EventListener
    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        cacheManager.cacheNames.forEach { cacheName ->
            logger.debug("Invalidating cache $cacheName")
            cacheManager.getCache(cacheName)?.clear()
        }
    }
}