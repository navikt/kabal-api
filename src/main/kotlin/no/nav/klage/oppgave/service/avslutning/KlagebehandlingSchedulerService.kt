package no.nav.klage.oppgave.service.avslutning

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.oppgave.clients.kaka.KakaApiGateway
import no.nav.klage.oppgave.domain.behandling.Behandling
import no.nav.klage.oppgave.domain.behandling.BehandlingWithKvalitetsvurdering
import no.nav.klage.oppgave.domain.kafka.EventType
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus.FEILET
import no.nav.klage.oppgave.domain.kafka.UtsendingStatus.IKKE_SENDT
import no.nav.klage.oppgave.eventlisteners.CleanupAfterBehandlingEventListener
import no.nav.klage.oppgave.service.BehandlingService
import no.nav.klage.oppgave.service.KafkaDispatcher
import no.nav.klage.oppgave.util.getLogger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

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

    @Scheduled(cron = "0 5 * * * *", initialDelay = 60_000)
    @SchedulerLock(name = "cleanupMergedDocuments")
    fun cleanupMergedDocuments() {
        logSchedulerMessage(functionName = ::cleanupMergedDocuments.name)
        cleanupAfterBehandlingEventListener.cleanupMergedDocuments()
    }

    //TODO: Hvorfor vente 2 minutter?
    @Scheduled(cron = "0 */2 * * * *", initialDelay = 60_000)
    @SchedulerLock(name = "avsluttBehandling")
    fun avsluttBehandling() {
        logSchedulerMessage(functionName = ::avsluttBehandling.name)
        val behandlingList: List<Behandling> = behandlingService.findBehandlingerForAvslutning()

        behandlingList.forEach { behandling ->
            if (behandling is BehandlingWithKvalitetsvurdering) {
                kakaApiGateway.finalizeBehandling(
                    behandlingService.getBehandlingEagerForReadWithoutCheckForAccess(
                        behandling.id
                    ) as BehandlingWithKvalitetsvurdering
                )
            }
            behandlingAvslutningService.avsluttBehandling(behandling.id)
        }
    }

    @Scheduled(cron = "0 */4 * * * *", initialDelay = 60_000)
    @SchedulerLock(name = "dispatchUnsentVedtakToKafka")
    fun dispatchUnsentVedtakToKafka() {
        logSchedulerMessage(functionName = ::dispatchUnsentVedtakToKafka.name)
        kafkaDispatcher.dispatchEventsToKafka(
            type = EventType.KLAGE_VEDTAK,
            utsendingStatusList = listOf(IKKE_SENDT, FEILET)
        )
    }

    @Scheduled(cron = "0 1/4 * * * *", initialDelay = 60_000)
    @SchedulerLock(name = "dispatchUnsentDVHStatsToKafka")
    fun dispatchUnsentDVHStatsToKafka() {
        logSchedulerMessage(functionName = ::dispatchUnsentDVHStatsToKafka.name)
        kafkaDispatcher.dispatchEventsToKafka(
            type = EventType.STATS_DVH,
            utsendingStatusList = listOf(IKKE_SENDT, FEILET)
        )
    }

    @Scheduled(cron = "0 2/4 * * * *", initialDelay = 60_000)
    @SchedulerLock(name = "dispatchUnsentBehandlingEventsToKafka")
    fun dispatchUnsentBehandlingEventsToKafka() {
        logSchedulerMessage(functionName = ::dispatchUnsentBehandlingEventsToKafka.name)
        kafkaDispatcher.dispatchEventsToKafka(
            type = EventType.BEHANDLING_EVENT,
            utsendingStatusList = listOf(IKKE_SENDT, FEILET)
        )
    }

    @Scheduled(cron = "0 3/4 * * * *", initialDelay = 60_000)
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