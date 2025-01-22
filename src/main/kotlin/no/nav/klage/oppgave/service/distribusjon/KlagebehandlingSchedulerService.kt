package no.nav.klage.oppgave.service.distribusjon

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.kodeverk.Type
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus.FEILET
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus.IKKE_SENDT
import no.nav.klage.oppgave.eventlisteners.CleanupAfterBehandlingEventListener
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.KafkaDispatcher
import no.nav.klage.oppgave.util.getLogger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class KlagebehandlingSchedulerService(
    private val behandlingService: BehandlingService,
    private val behandlingAvslutningService: BehandlingAvslutningService,
    private val kafkaDispatcher: KafkaDispatcher,
    private val kakaApiGateway: KakaApiGateway,
    private val cleanupAfterBehandlingEventListener: CleanupAfterBehandlingEventListener,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Scheduled(timeUnit = TimeUnit.MINUTES, fixedDelay = 60, initialDelay = 6)
    @SchedulerLock(name = "cleanupMergedDocuments")
    fun cleanupMergedDocuments() {
        logSchedulerMessage(functionName = ::cleanupMergedDocuments.name)
        cleanupAfterBehandlingEventListener.cleanupMergedDocuments()
    }

    //TODO: Hvorfor vente 2 minutter?
    @Scheduled(timeUnit = TimeUnit.MINUTES, fixedDelay = 2, initialDelay = 4)
    @SchedulerLock(name = "avsluttBehandling")
    fun avsluttBehandling() {
        logSchedulerMessage(functionName = ::avsluttBehandling.name)
        val behandlingIdList: List<Pair<UUID, Type>> = behandlingService.findBehandlingerForAvslutning()

        behandlingIdList.forEach { (id, type) ->
            if (type != Type.ANKE_I_TRYGDERETTEN) {
                kakaApiGateway.finalizeBehandling(
                    behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(
                        id
                    )
                )
            }
            behandlingAvslutningService.avsluttBehandling(id)
        }
    }

    @Scheduled(timeUnit = TimeUnit.MINUTES, fixedDelay = 4, initialDelay = 3)
    @SchedulerLock(name = "dispatchUnsentVedtakToKafka")
    fun dispatchUnsentVedtakToKafka() {
        logSchedulerMessage(functionName = ::dispatchUnsentVedtakToKafka.name)
        kafkaDispatcher.dispatchEventsToKafka(
            type = EventType.KLAGE_VEDTAK,
            utsendingStatusList = listOf(IKKE_SENDT, FEILET)
        )
    }

    @Scheduled(timeUnit = TimeUnit.MINUTES, fixedDelay = 4, initialDelay = 7)
    @SchedulerLock(name = "dispatchUnsentDVHStatsToKafka")
    fun dispatchUnsentDVHStatsToKafka() {
        logSchedulerMessage(functionName = ::dispatchUnsentDVHStatsToKafka.name)
        kafkaDispatcher.dispatchEventsToKafka(
            type = EventType.STATS_DVH,
            utsendingStatusList = listOf(IKKE_SENDT, FEILET)
        )
    }

    @Scheduled(timeUnit = TimeUnit.MINUTES, fixedDelay = 4, initialDelay = 10)
    @SchedulerLock(name = "dispatchUnsentBehandlingEventsToKafka")
    fun dispatchUnsentBehandlingEventsToKafka() {
        logSchedulerMessage(functionName = ::dispatchUnsentBehandlingEventsToKafka.name)
        kafkaDispatcher.dispatchEventsToKafka(
            type = EventType.BEHANDLING_EVENT,
            utsendingStatusList = listOf(IKKE_SENDT, FEILET)
        )
    }

    @Scheduled(timeUnit = TimeUnit.MINUTES, fixedDelay = 4, initialDelay = 10)
    @SchedulerLock(name = "dispatchUnsentMinsideMicrofrontendEventsToKafka")
    fun dispatchUnsentMinsideMicrofrontendEventsToKafka() {
        logSchedulerMessage(functionName = ::dispatchUnsentMinsideMicrofrontendEventsToKafka.name)
        kafkaDispatcher.dispatchEventsToKafka(
            type = EventType.MINSIDE_MICROFRONTEND_EVENT,
            utsendingStatusList = listOf(IKKE_SENDT, FEILET)
        )
    }

    private fun logSchedulerMessage(functionName: String) {
        logger.debug("$functionName is called by scheduler")
    }
}