package no.nav.klage.oppgave.service.distribusjon

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus.FEILET
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus.IKKE_SENDT
import no.nav.klage.oppgave.domain.klage.Klagebehandling
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.KafkaDispatcher
import no.nav.klage.oppgave.service.KlagebehandlingService
import no.nav.klage.oppgave.util.getLogger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.*

@Service
class KlagebehandlingSchedulerService(
    private val klagebehandlingService: KlagebehandlingService,
    private val behandlingService: BehandlingService,
    private val klagebehandlingDistribusjonService: KlagebehandlingDistribusjonService,
    private val kafkaDispatcher: KafkaDispatcher,
    private val kakaApiGateway: KakaApiGateway
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Scheduled(fixedDelay = 240000, initialDelay = 240000)
    @SchedulerLock(name = "distribuerVedtak")
    fun distribuerVedtak() {
        logger.debug("distribuerVedtak is called by scheduler")
        val klagebehandlingIdList: List<UUID> = klagebehandlingService.findKlagebehandlingForDistribusjon()

        klagebehandlingIdList.forEach { klagebehandlingId ->
            kakaApiGateway.finalizeKlagebehandling(
                behandlingService.getBehandlingForReadWithoutCheckForAccess(
                    klagebehandlingId
                ) as Klagebehandling
            )
            klagebehandlingDistribusjonService.distribuerKlagebehandling(klagebehandlingId)
        }
    }

    @Scheduled(fixedDelay = 240000, initialDelay = 300000)
    @SchedulerLock(name = "dispatchUnsentVedtakToKafka")
    fun dispatchUnsentVedtakToKafka() {
        logger.debug("dispatchUnsentVedtakToKafka is called by scheduler")
        kafkaDispatcher.dispatchEventsToKafka(
            type = EventType.KLAGE_VEDTAK,
            utsendingStatusList = listOf(IKKE_SENDT, FEILET)
        )
    }

    @Scheduled(fixedDelay = 240000, initialDelay = 360000)
    @SchedulerLock(name = "dispatchUnsentDVHStatsToKafka")
    fun dispatchUnsentDVHStatsToKafka() {
        logger.debug("dispatchUnsentDVHStatsToKafka is called by scheduler")
        kafkaDispatcher.dispatchEventsToKafka(
            type = EventType.STATS_DVH,
            utsendingStatusList = listOf(IKKE_SENDT, FEILET)
        )
    }
}