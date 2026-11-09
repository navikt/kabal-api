package no.nav.klage.oppgave.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.klagenotificationsapi.KlageNotificationsApiClient
import no.nav.klage.oppgave.config.SchedulerHealthGate
import no.nav.klage.oppgave.domain.notifications.CreateGainedAccessNotificationEvent
import no.nav.klage.oppgave.domain.notifications.CreateLostAccessNotificationEvent
import no.nav.klage.oppgave.domain.notifications.CreateNotificationEvent
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.util.UUID

@Service
class LostAccessService(
    private val behandlingRepository: BehandlingRepository,
    @Value($$"${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val klageNotificationsApiClient: KlageNotificationsApiClient,
    private val tilgangService: TilgangService,
    private val schedulerHealthGate: SchedulerHealthGate,
    transactionManager: PlatformTransactionManager,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
        private val jacksonObjectMapper = jacksonObjectMapper()
    }

    private val readOnlyTransactionTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    /**
     * Scheduled task that creates "lost access"-notifications (and gained access) for saksbehandlere who have lost access to behandlinger.
     * Note that klage-notifications-api is idempotent when it comes to creating notifications.
     *
     * Would have used events (instead of cron) if they were available for all cases.
     *
     * Intentionally not @Transactional: the job makes one access check (HTTP) per person in each tildelt behandling,
     * which takes about a minute in prod. Holding a DB connection for all of it starved the Hikari pool. The data
     * needed from the database is therefore read into [TildeltBehandling] in one short read-only transaction.
     *
     * Schedule:
     * - Every hour, Mon-Fri, 6:00-21:00
     * - At 10:00, 12:00, 16:00, 19:00 on Sat-Sun
     */
    @Scheduled(cron = "0 0 6-21 * * MON-FRI")
    @Scheduled(cron = "0 0 10,12,16,19 * * SAT-SUN")
    @SchedulerLock(name = "createLostAccessNotifications")
    fun createLostAccessNotifications() {
        if (!schedulerHealthGate.isReady()) return
        logger.debug("Checking for lost access notifications to create")
        val start = System.currentTimeMillis()

        val lostAccessNotifications = klageNotificationsApiClient.getLostAccessNotifications()
        logger.debug("Number of lost-access notifications already in the system: ${lostAccessNotifications.size}")

        val tildelteBehandlinger = readOnlyTransactionTemplate.execute { getTildelteBehandlinger() }

        // Create notifications where sbh have lost access
        tildelteBehandlinger.forEach { tildeltBehandling ->
            if (lostAccessNotifications.any {
                    it.behandlingId == tildeltBehandling.behandlingId && it.navIdent == tildeltBehandling.navIdent
                }
            ) {
                // already notified
                return@forEach
            }

            val access =
                tilgangService.getSaksbehandlerAccessToPersongalleri(
                    persongalleri = tildeltBehandling.persongalleri,
                    navIdent = tildeltBehandling.navIdent,
                )

            if (!access.access) {
                publishAccessNotificationEvent(
                    createNotificationEvent =
                        CreateLostAccessNotificationEvent(
                            type = CreateNotificationEvent.NotificationType.LOST_ACCESS,
                            message =
                                "Du har mistet tilgang til oppgaven grunnet: ${access.reason}. Be lederen din om å tildele saken " +
                                    "til noen andre eller gi deg tilgang.",
                            recipientNavIdent = tildeltBehandling.navIdent,
                            behandlingId = tildeltBehandling.behandlingId,
                            behandlingType = tildeltBehandling.type,
                            actorNavIdent = systembrukerIdent,
                            actorNavn = systembrukerIdent,
                            saksnummer = tildeltBehandling.saksnummer,
                            ytelse = tildeltBehandling.ytelse,
                            sourceCreatedAt = LocalDateTime.now(),
                        ),
                )
            }
        }

        // Are some available again?
        // Create notifications
        lostAccessNotifications.forEach { lostAccessNotification ->
            val behandling =
                tildelteBehandlinger.find {
                    it.behandlingId == lostAccessNotification.behandlingId &&
                        it.navIdent == lostAccessNotification.navIdent
                }

            if (behandling != null) {
                val access =
                    tilgangService.getSaksbehandlerAccessToPersongalleri(
                        persongalleri = behandling.persongalleri,
                        navIdent = behandling.navIdent,
                    )

                if (access.access) {
                    publishAccessNotificationEvent(
                        createNotificationEvent =
                            CreateGainedAccessNotificationEvent(
                                type = CreateNotificationEvent.NotificationType.GAINED_ACCESS,
                                message = "Du har nå tilgang til oppgaven.",
                                recipientNavIdent = behandling.navIdent,
                                behandlingId = behandling.behandlingId,
                                behandlingType = behandling.type,
                                actorNavIdent = systembrukerIdent,
                                actorNavn = systembrukerIdent,
                                saksnummer = behandling.saksnummer,
                                ytelse = behandling.ytelse,
                                sourceCreatedAt = LocalDateTime.now(),
                            ),
                    )
                }
            }
        }

        val end = System.currentTimeMillis()
        logger.debug("Time it took to check and create lost/gained access notifications: ${end - start} millis")
    }

    private fun getTildelteBehandlinger(): List<TildeltBehandling> =
        behandlingRepository
            .findByTildelingIsNotNullAndFerdigstillingIsNullAndFeilregistreringIsNull()
            .map { behandling ->
                TildeltBehandling(
                    behandlingId = behandling.id,
                    navIdent = behandling.tildeling?.saksbehandlerident!!,
                    type = behandling.type,
                    saksnummer = behandling.getSaksnummer(),
                    ytelse = behandling.ytelse,
                    persongalleri = tilgangService.getPersongalleriToCheckForBehandling(behandling),
                )
            }

    private fun publishAccessNotificationEvent(createNotificationEvent: CreateNotificationEvent) {
        kafkaInternalEventService.publishNotificationEvent(
            id = UUID.randomUUID(),
            jsonNode = jacksonObjectMapper.valueToTree(createNotificationEvent),
        )
    }

    private data class TildeltBehandling(
        val behandlingId: UUID,
        val navIdent: String,
        val type: Type,
        val saksnummer: String,
        val ytelse: Ytelse,
        val persongalleri: List<String>,
    )
}
