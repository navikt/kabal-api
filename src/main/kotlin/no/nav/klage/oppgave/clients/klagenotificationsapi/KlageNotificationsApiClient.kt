package no.nav.klage.oppgave.clients.klagenotificationsapi

import no.nav.klage.oppgave.clients.klagenotificationsapi.domain.LostAccessNotificationResponse
import no.nav.klage.oppgave.clients.klagenotificationsapi.domain.TransferNotificationOwnershipRequest
import no.nav.klage.oppgave.clients.klagenotificationsapi.domain.UnreadCountResponse
import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

@Component
class KlageNotificationsApiClient(
    private val klageNotificationsApiWebClient: WebClient,
    private val tokenUtil: TokenUtil,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = getLogger(javaClass.enclosingClass)
    }

    @Retryable
    fun deleteNotificationsForBehandling(behandlingId: UUID) {
        logger.debug("Deleting notifications for behandling {}", behandlingId)

        klageNotificationsApiWebClient.delete()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/admin/notifications/behandling/{behandlingId}")
                    .build(behandlingId)
            }
            .header("Authorization", "Bearer ${tokenUtil.getAppAccessTokenWithKlageNotificationsApiScope()}")
            .retrieve()
            .toBodilessEntity()
            .block()
    }

    @Retryable
    fun getUnreadCount(behandlingId: UUID): Int {
        logger.debug("Getting unread count for behandling {}", behandlingId)

        return klageNotificationsApiWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/notifications/behandling/{behandlingId}/unread-count")
                    .build(behandlingId)
            }
            .header("Authorization", "Bearer ${tokenUtil.getAppAccessTokenWithKlageNotificationsApiScope()}")
            .retrieve()
            .bodyToMono(UnreadCountResponse::class.java)
            .block()!!.unreadMessageCount
    }

    @Retryable
    fun transferNotificationOwnership(behandlingId: UUID, newNavIdent: String) {
        logger.debug("Transferring notification ownership for behandling {} to {}", behandlingId, newNavIdent)

        klageNotificationsApiWebClient.post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/admin/notifications/behandling/{behandlingId}/transfer-ownership")
                    .build(behandlingId)
            }
            .header("Authorization", "Bearer ${tokenUtil.getAppAccessTokenWithKlageNotificationsApiScope()}")
            .bodyValue(TransferNotificationOwnershipRequest(newNavIdent = newNavIdent))
            .retrieve()
            .toBodilessEntity()
            .block()
    }

    @Retryable
    fun getLostAccessNotifications(): List<LostAccessNotificationResponse> {
        logger.debug("Getting lost access notifications")

        return klageNotificationsApiWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/admin/notifications/lost-access")
                    .build()
            }
            .header("Authorization", "Bearer ${tokenUtil.getAppAccessTokenWithKlageNotificationsApiScope()}")
            .retrieve()
            .bodyToFlux(LostAccessNotificationResponse::class.java)
            .collectList()
            .block()!!
    }

}