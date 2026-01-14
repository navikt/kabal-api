package no.nav.klage.oppgave.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.oppgave.clients.klagenotificationsapi.KlageNotificationsApiClient
import no.nav.klage.oppgave.config.SchedulerHealthGate
import no.nav.klage.oppgave.domain.notifications.CreateGainedAccessNotificationEvent
import no.nav.klage.oppgave.domain.notifications.CreateLostAccessNotificationEvent
import no.nav.klage.oppgave.domain.notifications.CreateNotificationEvent
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import no.nav.klage.oppgave.util.ourJsonMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class LostAccessService(
    private val behandlingRepository: BehandlingRepository,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val klageNotificationsApiClient: KlageNotificationsApiClient,
    private val tilgangService: TilgangService,
    private val schedulerHealthGate: SchedulerHealthGate,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
        private val objectMapper = ourJsonMapper()
    }

    /**
     * Scheduled task that creates "lost access"-notifications (and gained access) for saksbehandlere who have lost access to behandlinger.
     * Note that klage-notifications-api is idempotent when it comes to creating notifications.
     *
     * Would have used events (instead of cron) if they were available for all cases.
     *
     * Schedule:
     * - Every 15 minutes, Mon-Fri, 6:00-17:59
     * - Every hour, Mon-Fri, 18:00-21:00
     * - At 10:00, 12:00, 16:00, 19:00 on Sat-Sun
     */
    @Transactional(readOnly = true)
    @Scheduled(cron = "0 0/15 6-17 * * MON-FRI")
    @Scheduled(cron = "0 0 19-21 * * MON-FRI")
    @Scheduled(cron = "0 0 10,12,16,19 * * SAT-SUN")
    @SchedulerLock(name = "createLostAccessNotifications")
    fun createLostAccessNotifications() {
        if (!schedulerHealthGate.isReady()) return
        logger.debug("Checking for lost access notifications to create")
        val start = System.currentTimeMillis()
        val tildelteBehandlinger =
            behandlingRepository.findByTildelingIsNotNullAndFerdigstillingIsNullAndFeilregistreringIsNull()

        val lostAccessNotifications = klageNotificationsApiClient.getLostAccessNotifications()
        logger.debug("Number of lost-access notifications already in the system: ${lostAccessNotifications.size}")

        //Create notifications where sbh have lost access
        tildelteBehandlinger.forEach { behandling ->
            val tildeltSaksbehandlerIdent = behandling.tildeling?.saksbehandlerident!!
            if (lostAccessNotifications.any {
                    it.behandlingId == behandling.id && it.navIdent == tildeltSaksbehandlerIdent
                }) {
                //already notified
                return@forEach
            }

            val access = tilgangService.hasSaksbehandlerAccessTo(
                fnr = behandling.sakenGjelder.partId.value,
                navIdent = tildeltSaksbehandlerIdent,
            )

            if (!access.access) {
                publishAccessNotificationEvent(
                    createNotificationEvent = CreateLostAccessNotificationEvent(
                        type = CreateNotificationEvent.NotificationType.LOST_ACCESS,
                        message = "Du har mistet tilgang til oppgaven grunnet: ${access.reason}. Be lederen din om å tildele saken til noen andre eller gi deg tilgang.",
                        recipientNavIdent = tildeltSaksbehandlerIdent,
                        behandlingId = behandling.id,
                        behandlingType = behandling.type,
                        actorNavIdent = systembrukerIdent,
                        actorNavn = systembrukerIdent,
                        saksnummer = behandling.fagsakId,
                        ytelse = behandling.ytelse,
                        sourceCreatedAt = LocalDateTime.now(),
                    ),
                )
            }
        }

        //Are some available again?
        //Create notifications
        lostAccessNotifications.forEach { lostAccessNotification ->
            val behandling =
                tildelteBehandlinger.find { it.id == lostAccessNotification.behandlingId && it.tildeling?.saksbehandlerident == lostAccessNotification.navIdent }

            if (behandling != null) {
                val tildeltSaksbehandlerIdent = behandling.tildeling?.saksbehandlerident!!

                val access = tilgangService.hasSaksbehandlerAccessTo(
                    fnr = behandling.sakenGjelder.partId.value,
                    navIdent = tildeltSaksbehandlerIdent,
                )

                if (access.access) {
                    publishAccessNotificationEvent(
                        createNotificationEvent = CreateGainedAccessNotificationEvent(
                            type = CreateNotificationEvent.NotificationType.GAINED_ACCESS,
                            message = "Du har nå tilgang til oppgaven.",
                            recipientNavIdent = tildeltSaksbehandlerIdent,
                            behandlingId = behandling.id,
                            behandlingType = behandling.type,
                            actorNavIdent = systembrukerIdent,
                            actorNavn = systembrukerIdent,
                            saksnummer = behandling.fagsakId,
                            ytelse = behandling.ytelse,
                            sourceCreatedAt = LocalDateTime.now(),
                        )
                    )
                }
            }
        }

        val end = System.currentTimeMillis()
        logger.debug("Time it took to check and create lost/gained access notifications: ${end - start} millis")
    }

    private fun publishAccessNotificationEvent(
        createNotificationEvent: CreateNotificationEvent
    ) {
        kafkaInternalEventService.publishNotificationEvent(
            id = UUID.randomUUID(),
            jsonNode = objectMapper.valueToTree(createNotificationEvent)
        )
    }
}