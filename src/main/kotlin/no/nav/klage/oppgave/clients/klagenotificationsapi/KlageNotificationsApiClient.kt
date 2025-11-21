package no.nav.klage.oppgave.clients.klagenotificationsapi

import no.nav.klage.oppgave.util.TokenUtil
import no.nav.klage.oppgave.util.getLogger
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

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
    fun deleteNotificationsForBehandling(behandlingId: String) {
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
    fun validateNoUnreadNotifications(behandlingId: String) {
        logger.debug("Validating no unread notifications for behandling {}", behandlingId)

        klageNotificationsApiWebClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/admin/notifications/behandling/{behandlingId}/validate-no-unread")
                    .build(behandlingId)
            }
            .header("Authorization", "Bearer ${tokenUtil.getAppAccessTokenWithKlageNotificationsApiScope()}")
            .retrieve()
            .toBodilessEntity()
            .block()
    }

}