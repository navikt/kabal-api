package no.nav.klage.oppgave.config

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import no.nav.klage.oppgave.service.ElasticsearchService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.annotation.PreDestroy


@Configuration
class FunksjonelleGaugesConfiguration {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Bean
    fun registerFunctionalStats(elasticsearchService: ElasticsearchService): MeterBinder {
        return MeterBinder { registry: MeterRegistry ->
            Gauge.builder("funksjonell.ikketildelt") { elasticsearchService.countIkkeTildelt() }.register(registry)
            Gauge.builder("funksjonell.tildelt") { elasticsearchService.countTildelt() }.register(registry)
            Gauge.builder("funksjonell.medunderskrivervalgt") { elasticsearchService.countMedunderskriverValgt() }
                .register(registry)
            Gauge.builder("funksjonell.sendttilmedunderskriver") { elasticsearchService.countSendtTilMedunderskriver() }
                .register(registry)
            Gauge.builder("funksjonell.returnerttilsaksbehandler") { elasticsearchService.countReturnertTilSaksbehandler() }
                .register(registry)
            Gauge.builder("funksjonell.avsluttet") { elasticsearchService.countAvsluttet() }.register(registry)
            Gauge.builder("funksjonell.antallsaksdokumenterpaaavsluttedebehandlinger.median") { elasticsearchService.countAntallSaksdokumenterIAvsluttedeBehandlingerMedian() }
                .register(registry)
            //TODO: Egentlig ønsker jeg å registrere antall saksdokumenter per klagebehandling, med klagebehandlingId'en som en tag i gaugen. Men hvordan i all verden gjør jeg det??
        }
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
        logger.info("We have received a SIGTERM (?)")
        if (!registry.isClosed) registry.close()
    }
}