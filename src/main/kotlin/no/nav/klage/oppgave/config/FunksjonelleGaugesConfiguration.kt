package no.nav.klage.oppgave.config

import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PreDestroy
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration


@Configuration
class FunksjonelleGaugesConfiguration {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }
}

@Configuration
class MicrometerGracefulShutdownConfiguration {

    @Bean
    fun registryCloser(registry: MeterRegistry) = RegistryCloser(registry)
}

class RegistryCloser(private val registry: MeterRegistry) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @PreDestroy
    fun onDestroy() {
        logger.debug("We have received a SIGTERM (?)")
        if (!registry.isClosed) registry.close()
    }
}