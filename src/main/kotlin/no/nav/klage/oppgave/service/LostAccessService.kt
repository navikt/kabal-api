package no.nav.klage.oppgave.service

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import no.nav.klage.kodeverk.PartIdType
import no.nav.klage.kodeverk.Type
import no.nav.klage.kodeverk.ytelse.Ytelse
import no.nav.klage.oppgave.clients.egenansatt.EgenAnsattService
import no.nav.klage.oppgave.clients.klagenotificationsapi.KlageNotificationsApiClient
import no.nav.klage.oppgave.domain.notifications.CreateGainedAccessNotificationEvent
import no.nav.klage.oppgave.domain.notifications.CreateLostAccessNotificationEvent
import no.nav.klage.oppgave.domain.notifications.CreateNotificationEvent
import no.nav.klage.oppgave.repositories.BehandlingRepository
import no.nav.klage.oppgave.util.getLogger
import no.nav.klage.oppgave.util.getTeamLogger
import no.nav.klage.oppgave.util.ourJacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class LostAccessService(
    private val behandlingRepository: BehandlingRepository,
    private val saksbehandlerService: SaksbehandlerService,
    @Value("\${SYSTEMBRUKER_IDENT}") private val systembrukerIdent: String,
    private val personService: PersonService,
    private val egenAnsattService: EgenAnsattService,
    private val kafkaInternalEventService: KafkaInternalEventService,
    private val klageNotificationsApiClient: KlageNotificationsApiClient,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
        private val teamLogger = getTeamLogger()
        private val objectMapper = ourJacksonObjectMapper()
    }

    /**
     * Scheduled task that creates "lost access"-notifications (and gained access) for saksbehandlere who have lost access to behandlinger.
     * Note that klage-notifications-api is idempotent when it comes to creating notifications.
     */
    @Transactional(readOnly = true)
    @Scheduled(cron = "0 0/3 * * * ?")
    @SchedulerLock(name = "createLostAccessNotifications")
    fun createLostAccessNotifications() {
        logger.debug("Checking for lost access notifications to create")
        val start = System.currentTimeMillis()
        val tildelteBehandlinger =
            behandlingRepository.findByTildelingIsNotNullAndFerdigstillingIsNullAndFeilregistreringIsNull()

        val lostAccessNotifications = klageNotificationsApiClient.getLostAccessNotifications()
        logger.debug("Number of lost-access notifications already in the system: ${lostAccessNotifications.size}")

        tildelteBehandlinger.forEach { behandling ->
            val tildeltSaksbehandlerIdent = behandling.tildeling?.saksbehandlerident!!
            val (lostAccessMessage, gainedAccessMessage) = if (behandling.sakenGjelder.partId.type == PartIdType.PERSON) {
                try {
                    val person = personService.getPersonInfo(behandling.sakenGjelder.partId.value)

                    val hasLostAccessNotification = lostAccessNotifications.any {
                        it.behandlingId == behandling.id && it.navIdent == tildeltSaksbehandlerIdent
                    }

                    logger.debug("Has lost-access notification: $hasLostAccessNotification")

                    when {
                        person.harBeskyttelsesbehovStrengtFortrolig() -> {
                            if (!saksbehandlerService.hasStrengtFortroligRole(
                                    ident = tildeltSaksbehandlerIdent,
                                    useCache = true
                                )
                            ) {
                                "Du har mistet tilgang til oppgaven fordi den gjelder en person med strengt fortrolig adresse. Be lederen din om å tildele saken til noen andre eller gi deg tilgang." to null
                            } else {
                                if (hasLostAccessNotification) {
                                    logger.debug("Gained access for strengt fortrolig behandling {}", behandling.id)
                                    null to "Du har nå tilgang til oppgaven."
                                } else {
                                    null to null
                                }
                            }
                        }

                        person.harBeskyttelsesbehovFortrolig() -> {
                            if (!saksbehandlerService.hasFortroligRole(
                                    ident = tildeltSaksbehandlerIdent,
                                    useCache = true
                                )
                            ) {
                                "Du har mistet tilgang til oppgaven fordi den gjelder en person med fortrolig adresse. Be lederen din om å tildele saken til noen andre eller gi deg tilgang." to null
                            } else {
                                if (hasLostAccessNotification) {
                                    logger.debug("Gained access for fortrolig behandling {}", behandling.id)
                                    null to "Du har nå tilgang til oppgaven."
                                } else {
                                    null to null
                                }
                            }
                        }

                        egenAnsattService.erEgenAnsatt(person.foedselsnr) -> {
                            if (!saksbehandlerService.hasEgenAnsattRole(
                                    ident = tildeltSaksbehandlerIdent,
                                    useCache = true
                                )
                            ) {
                                "Du har mistet tilgang til oppgaven fordi den gjelder egen ansatt. Be lederen din om å tildele saken til noen andre eller gi deg tilgang." to null
                            }  else {
                                if (hasLostAccessNotification) {
                                    logger.debug("Gained access for egen ansatt behandling {}", behandling.id)
                                    null to "Du har nå tilgang til oppgaven."
                                } else {
                                    null to null
                                }
                            }
                        }

                        else -> null to null
                    }

                } catch (e: Exception) {
                    teamLogger.debug("Couldn't check person", e)
                    null to null
                }
            } else null to null

            if (lostAccessMessage != null && gainedAccessMessage != null) {
                throw IllegalStateException("Both lostAccessMessage and gainedAccessMessage are not null for behandling ${behandling.id}")
            }

            if (lostAccessMessage != null || gainedAccessMessage != null) {
                publishLostAccessNotificationEvent(
                    message = lostAccessMessage ?: gainedAccessMessage!!,
                    utfoerendeIdent = systembrukerIdent,
                    utfoerendeName = systembrukerIdent,
                    tildeltSaksbehandlerIdent = tildeltSaksbehandlerIdent,
                    behandlingId = behandling.id,
                    behandlingType = behandling.type,
                    saksnummer = behandling.fagsakId,
                    ytelse = behandling.ytelse,
                    isLostAccess = lostAccessMessage != null,
                )
            }
        }
        val end = System.currentTimeMillis()
        logger.debug("Time it took to check and create lost/gained access notifications: ${end - start} millis")
    }

    private fun publishLostAccessNotificationEvent(
        message: String,
        utfoerendeIdent: String,
        utfoerendeName: String,
        tildeltSaksbehandlerIdent: String,
        behandlingId: UUID,
        behandlingType: Type,
        saksnummer: String,
        ytelse: Ytelse,
        isLostAccess: Boolean,
    ) {
        logger.debug(
            "Publishing {}-access notification event for behandling {} ",
            if (isLostAccess) "lost" else "gained",
            behandlingId,
        )

        val createEvent = if (isLostAccess) {
            CreateLostAccessNotificationEvent(
                type = CreateNotificationEvent.NotificationType.LOST_ACCESS,
                message = message,
                recipientNavIdent = tildeltSaksbehandlerIdent,
                behandlingId = behandlingId,
                behandlingType = behandlingType,
                actorNavIdent = utfoerendeIdent,
                actorNavn = utfoerendeName,
                saksnummer = saksnummer,
                ytelse = ytelse,
                sourceCreatedAt = LocalDateTime.now(),
            )
        } else {
            CreateGainedAccessNotificationEvent(
                type = CreateNotificationEvent.NotificationType.GAINED_ACCESS,
                message = message,
                recipientNavIdent = tildeltSaksbehandlerIdent,
                behandlingId = behandlingId,
                behandlingType = behandlingType,
                actorNavIdent = utfoerendeIdent,
                actorNavn = utfoerendeName,
                saksnummer = saksnummer,
                ytelse = ytelse,
                sourceCreatedAt = LocalDateTime.now(),
            )
        }

        kafkaInternalEventService.publishNotificationEvent(
            id = UUID.randomUUID(),
            jsonNode = objectMapper.valueToTree(createEvent)
        )
    }
}